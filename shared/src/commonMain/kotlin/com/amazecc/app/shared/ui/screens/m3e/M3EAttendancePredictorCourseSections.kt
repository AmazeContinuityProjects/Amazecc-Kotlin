package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.AttendancePredictor
import com.amazecc.app.shared.domain.ScheduleResult
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorState
import com.amazecc.app.shared.domain.overhaul.M3EPredictorCalendarDay
import com.amazecc.app.shared.ui.m3e.components.M3EAccordionChevron
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-139b: sections 7-10 of the web `OverallAttendancePredictor.tsx` - the course
 * breakdown, its per-subject date skipper, and the smart advisor card.
 *
 * Fully stateless: every interaction leaves through [onAction]. [ScheduleResult]
 * now carries its own real `courseCode` from the projection, so each row is
 * identified honestly and the per-date skipper targets the correct subject. Only
 * a legacy result with a blank code falls back to an explicitly positional label.
 *
 * No day cards map / working days / effective map are carried in the state, so
 * candidate meeting dates are derived honestly from [M3EPredictorCalendarDay]
 * working days whose real `effectiveWeekday` matches the projection's real
 * `meetingDays`, bounded by the course's real lock date.
 */
@Composable
fun M3EAttendancePredictorCourseSections(
    state: M3EAttendancePredictorState,
    onAction: (M3EAttendancePredictorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val target = state.targetPercent
    val rows = rememberCourseRows(state)
    val query = state.searchQuery.trim()

    val filters = listOf(
        FilterOption("ALL", "All"),
        FilterOption("SAFE", "Safe (\u2265$target%)"),
        FilterOption("AT_RISK", "At Risk (<$target%)"),
        FilterOption("THEORY", "Theory"),
        FilterOption("LAB", "Lab")
    )
    val visible = rows.filter { row ->
        val matchesSearch = query.isBlank() ||
            row.label.contains(query, ignoreCase = true) ||
            row.result.meetingDays.any { it.contains(query, ignoreCase = true) }
        val matchesFilter = when (state.filter) {
            "SAFE" -> row.result.predictedPct >= target
            "AT_RISK" -> row.result.predictedPct < target
            "THEORY" -> !row.result.isLab
            "LAB" -> row.result.isLab
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Group header - flat typography, no fake control.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "COURSE SIMULATION BREAKDOWN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = colors.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onAction(M3EAttendancePredictorAction.UpdateSearch(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search courses by code, title, or faculty...",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.primary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLowest,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant
            )
        )

        M3EConnectedButtonGroup(
            items = filters,
            selectedItem = filters.firstOrNull { it.value == state.filter } ?: filters.first(),
            onItemSelected = { onAction(M3EAttendancePredictorAction.SetFilter(it.value)) },
            labelProvider = { it.label }
        )

        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "No courses match your filter",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = "Try a different filter, or clear the search.",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "COURSES \u00B7 ${visible.size}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                visible.forEachIndexed { index, row ->
                    M3ECourseSectionRow(
                        row = row,
                        state = state,
                        onAction = onAction
                    )
                    if (index != visible.lastIndex) {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }

        M3EAttendanceAdvisorCard(target = target)
    }
}

/** A filter segment's stable value and its target-dependent label. */
private data class FilterOption(val value: String, val label: String)

/** One projection plus the only honest course identity the state can supply. */
private data class CourseRow(
    val result: ScheduleResult,
    val code: String?,
    val label: String
)

/**
 * [ScheduleResult] carries its own real [ScheduleResult.courseCode] from the
 * projection, so every row is identified honestly. Only a legacy result with no
 * code gets an explicitly positional label (never an invented course identity).
 */
@Composable
private fun rememberCourseRows(state: M3EAttendancePredictorState): List<CourseRow> =
    state.courseResults.mapIndexed { index, result ->
        val code = result.courseCode.takeIf { it.isNotBlank() }
        CourseRow(
            result = result,
            code = code,
            label = code ?: "Section ${index + 1}"
        )
    }

@Composable
private fun M3ECourseSectionRow(
    row: CourseRow,
    state: M3EAttendancePredictorState,
    onAction: (M3EAttendancePredictorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val result = row.result
    val isSafe = result.predictedPct >= state.targetPercent
    val isClose = isSafe && result.predictedPct < state.targetPercent + 4
    val lockDate = AttendancePredictor.courseLockDate(state.mode, result.isLab, state.milestoneDates)
    val locked = lockDate != null && lockDate <= AttendancePredictor.startOfDay(
        Clock.System.now().toEpochMilliseconds()
    )
    val expanded = state.expandedCourseCode == row.code
    val tone = when {
        locked -> colors.onSurfaceVariant
        isSafe -> colors.success
        isClose -> colors.warning
        else -> colors.error
    }
    val icon = if (result.isLab) Icons.Rounded.Science else Icons.Rounded.MenuBook
    val skipCount = row.code?.let { state.courseSkips[it]?.size } ?: result.skippedCount
    val subtitle = buildString {
        append(result.meetingDays.joinToString(", ").ifEmpty { "No timetable" })
        append(" \u00B7 now ")
        append(AttendancePredictor.formatPct(result.currentPct))
        append('%')
    }

    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = if (expanded) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableA11y(
                        onClickLabel = if (expanded) {
                            "Hide ${row.label}"
                        } else {
                            "Choose dates to skip for ${row.label}"
                        },
                        mergeDescendants = false,
                        onClick = { onAction(M3EAttendancePredictorAction.ToggleCourseExpanded(row.code)) }
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tone.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (locked) colors.onSurfaceVariant else colors.onSurface,
                            maxLines = 1
                        )
                        if (skipCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.warning.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$skipCount skip${if (skipCount == 1) "" else "s"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.warning
                                )
                            }
                        }
                        if (locked) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.AcUnit,
                                contentDescription = "Attendance locked",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${AttendancePredictor.formatPct(result.predictedPct)}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = tone,
                    maxLines = 1
                )
                M3EAccordionChevron(
                    isExpanded = expanded,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = M3ETheme.motion.spatialDefault()) +
                    fadeIn(animationSpec = M3ETheme.motion.effectsDefault()),
                exit = shrinkVertically(animationSpec = M3ETheme.motion.spatialDefault()) +
                    fadeOut(animationSpec = M3ETheme.motion.effectsDefault())
            ) {
                M3EPredictorSubjectSkipper(
                    row = row,
                    lockDate = lockDate,
                    locked = locked,
                    calendarDays = state.calendarDays,
                    skips = row.code?.let { state.courseSkips[it].orEmpty() }.orEmpty(),
                    onAction = onAction,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun M3EPredictorSubjectSkipper(
    row: CourseRow,
    lockDate: Long?,
    locked: Boolean,
    calendarDays: List<M3EPredictorCalendarDay>,
    skips: Set<Long>,
    onAction: (M3EAttendancePredictorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val code = row.code
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            locked -> {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainerLow)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AcUnit,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = buildString {
                            append("Attendance is frozen")
                            if (lockDate != null) append(" as of ${formatShortDate(lockDate)}")
                            append(if (row.result.isLab) " for lab" else " for theory")
                            append(", so there is nothing left to skip.")
                        },
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            code == null -> {
                Text(
                    text = "This course has no code in the supplied state, so per-date skipping is unavailable.",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainerLow)
                        .padding(12.dp)
                )
            }
            else -> {
                val days = meetingDays(row.result, calendarDays, lockDate)
                if (days.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceContainerLow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "No classes left to project",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "Every scheduled class for this course is in the past, or its attendance is already locked.",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = if (row.result.isLab) {
                            "A lab session counts as two hours, so skipping one costs two classes."
                        } else {
                            "Skipped dates count as absent for this subject only \u2014 nothing else on the timetable moves."
                        },
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                    days.forEach { day ->
                        M3EPredictorSkipDateRow(
                            day = day,
                            skipped = skips.contains(day.date),
                            isLab = row.result.isLab,
                            onClick = {
                                onAction(M3EAttendancePredictorAction.ToggleCourseSkip(code, day.date))
                            }
                        )
                    }
                    if (skips.isNotEmpty()) {
                        // The immutable contract has no "clear skips" action. Toggling each
                        // currently skipped date emits real, subject-isolated removals; the
                        // button is omitted when there is nothing it could honestly clear.
                        TextButton(
                            onClick = {
                                skips.forEach { date ->
                                    onAction(M3EAttendancePredictorAction.ToggleCourseSkip(code, date))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = colors.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Clear ${skips.size} skipped date${if (skips.size == 1) "" else "s"}",
                                fontSize = 12.sp,
                                color = colors.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Working days supplied by the host, narrowed to this course's real meeting
 * weekdays and to dates before the course's real lock date. No dates are invented.
 */
private fun meetingDays(
    result: ScheduleResult,
    calendarDays: List<M3EPredictorCalendarDay>,
    lockDate: Long?
): List<M3EPredictorCalendarDay> {
    val meeting = result.meetingDays.map { AttendancePredictor.normalizeDay(it) }.toSet()
    if (meeting.isEmpty()) return emptyList()
    return calendarDays
        .asSequence()
        .filter { AttendancePredictor.normalizeDay(it.effectiveWeekday) in meeting }
        .filter { lockDate == null || it.date < lockDate }
        .sortedBy { it.date }
        .toList()
}

@Composable
private fun M3EPredictorSkipDateRow(
    day: M3EPredictorCalendarDay,
    skipped: Boolean,
    isLab: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val whenText = if (skipped) {
        "Will be marked absent${if (isLab) " (2 hrs)" else ""}"
    } else {
        "Scheduled class"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (skipped) colors.tertiaryContainer else colors.surfaceContainerLowest)
            .clickableA11y(
                onClickLabel = if (skipped) {
                    "Unskip ${day.weekdayShort} ${day.dayNumber} ${day.monthLabel}"
                } else {
                    "Skip ${day.weekdayShort} ${day.dayNumber} ${day.monthLabel}"
                },
                mergeDescendants = false,
                onClick = onClick
            )
            .semantics { selected = skipped }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${day.weekdayShort.uppercase()} ${day.dayNumber} \u00B7 ${day.monthLabel}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (skipped) colors.onTertiaryContainer else colors.onSurface
        )
        Text(
            text = "$whenText \u00B7 ${day.classCount} cl",
            fontSize = 11.sp,
            color = if (skipped) colors.onTertiaryContainer else colors.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.warning.copy(alpha = if (skipped) 0.24f else 0.10f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (skipped) "Skipped" else "Skip",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun M3EAttendanceAdvisorCard(target: Int, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.primary.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Science,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Smart Attendance Insights & Exam Guidelines",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = colors.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            text = advisorBullet(
                "Exam Freeze Rule:",
                "Attendance freezes three days before CAT I and CAT II, so classes from that day onward are no longer counted."
            ),
            fontSize = 11.sp,
            color = colors.onSurfaceVariant
        )
        Text(
            text = advisorBullet(
                "Lab Session Weight:",
                "Each scheduled lab session accounts for 2 continuous hours, meaning 1 lab absence is equal to 2 missed classes."
            ),
            fontSize = 11.sp,
            color = colors.onSurfaceVariant
        )
        Text(
            text = advisorBullet(
                "Target Safety:",
                "Keeping attendance at or above $target% ensures full eligibility for CAT and FAT examinations."
            ),
            fontSize = 11.sp,
            color = colors.onSurfaceVariant
        )
        Text(
            text = advisorBullet(
                "Real-Time Simulation:",
                "Whole-day toggles and per-subject skipped dates simulate your projected percentages instantly without modifying actual portal records."
            ),
            fontSize = 11.sp,
            color = colors.onSurfaceVariant
        )
    }
}

private fun advisorBullet(label: String, body: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append(label) }
    append(' ')
    append(body)
}

/** `5 Aug`, from a real epoch-millisecond lock date. */
private fun formatShortDate(epochMillis: Long): String = runCatching {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    "${date.dayOfMonth} ${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
}.getOrDefault("")

@Preview
@Composable
private fun M3EAttendancePredictorCourseSectionsPreview() {
    com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme(isDark = false) {
        M3EAttendancePredictorCourseSections(
            state = com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorState(),
            onAction = {}
        )
    }
}
