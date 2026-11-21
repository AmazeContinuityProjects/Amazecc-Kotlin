package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.AttendancePredictor
import com.amazecc.app.shared.domain.CAT_LOCK_OFFSET_DAYS
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-139a: Material 3 Expressive shell for the ATTENDANCE predictor/simulator
 * (sections 1–6 of the web component). Fully stateless: [state] is immutable
 * and every interaction goes out through [onAction].
 *
 * The marks/GPA predictor is a separate screen; this one simulates leaves,
 * off-days and per-course skips against the real [com.amazecc.app.shared.domain.OverallSummary].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun M3EAttendancePredictorScreen(
    state: M3EAttendancePredictorState,
    onAction: (M3EAttendancePredictorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    // Exact web copy; rendered uppercase like M3EPageShell's eyebrow.
    val eyebrow = "Attendance · Predictor"

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
            // ── 1. HEADER ────────────────────────────────────────────────
            item(key = "attendance_predictor_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    IconButton(
                        onClick = { onAction(M3EAttendancePredictorAction.NavigateBack) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = eyebrow.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Attendance Predictor & Simulator",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "Projected ${AttendancePredictor.formatPct(state.summary.predictedPct)}% · " +
                                "target ${state.targetPercent}%",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Target threshold + reset, on one flat row (no nested cards).
            item(key = "attendance_predictor_targets") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3EConnectedButtonGroup(
                        items = listOf(75, 80, 85, 90),
                        selectedItem = state.targetPercent,
                        onItemSelected = { onAction(M3EAttendancePredictorAction.SetTarget(it)) },
                        labelProvider = { "$it%" },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    FilledTonalButton(
                        onClick = { onAction(M3EAttendancePredictorAction.ResetAll) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "Reset all simulated leaves and skips"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(text = "Reset", fontSize = 12.sp)
                    }
                }
            }

            // ── 2. SUMMARY ROW (all values from state.summary) ───────────
            item(key = "attendance_predictor_summary") {
                M3EAttendanceSummaryRow(state = state)
            }

            // ── 3. LOCK EXPLAINER ────────────────────────────────────────
            item(key = "attendance_predictor_lock") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AcUnit,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = lockExplainer(state),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 4. MILESTONE STRIP ───────────────────────────────────────
            item(key = "attendance_predictor_milestones") {
                val options = milestoneOptions(state)
                M3EConnectedButtonGroup(
                    items = options.map { it.id },
                    selectedItem = state.mode,
                    onItemSelected = { id ->
                        // An option with no milestone date is inert: the label
                        // already says "(no data)" rather than offering a
                        // simulation that cannot be computed.
                        if (options.firstOrNull { it.id == id }?.available == true) {
                            onAction(M3EAttendancePredictorAction.SetMode(id))
                        }
                    },
                    labelProvider = { id ->
                        val option = options.firstOrNull { it.id == id }
                        when {
                            option == null -> id
                            !option.available -> "${option.label} (no data)"
                            option.dateLabel != null -> "${option.label} (${option.dateLabel})"
                            else -> option.label
                        }
                    }
                )
            }

            // ── 5. QUICK ACTIONS ─────────────────────────────────────────
            item(key = "attendance_predictor_quick_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onAction(M3EAttendancePredictorAction.AttendAll) },
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(text = "Attend All", fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = { onAction(M3EAttendancePredictorAction.SkipFridays) },
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(text = "Skip Fridays", fontSize = 12.sp)
                    }
                }
            }

            // ── 6. INTERACTIVE DAY SIMULATOR ─────────────────────────────
            item(key = "attendance_predictor_calendar") {
                com.amazecc.app.shared.ui.m3e.components.M3EAttendancePredictorCalendar(
                    state = state,
                    onAction = onAction
                )
            }

            // ── COURSE BREAKDOWN (provided by another worker) ─────────────
            item(key = "attendance_predictor_courses") {
                M3EAttendancePredictorCourseSections(state = state, onAction = onAction)
            }

            // 140dp: twice the nav bar height, so the last course row clears it.
            item(key = "attendance_predictor_bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun M3EAttendanceSummaryRow(
    state: M3EAttendancePredictorState,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val summary = state.summary
    val projectedTone = if (summary.predictedPct >= state.targetPercent) colors.success else colors.error
    val riskTone = if (summary.atRiskCount > 0) colors.error else colors.success
    val deltaText = if (summary.delta >= 0) {
        "+${AttendancePredictor.formatPct(summary.delta)}"
    } else {
        AttendancePredictor.formatPct(summary.delta)
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EAttendanceSummaryCell(
                label = "Projected Avg",
                value = "${AttendancePredictor.formatPct(summary.predictedPct)}%",
                sub = "Now ${AttendancePredictor.formatPct(summary.currentPct)}%",
                accent = projectedTone,
                badge = deltaText,
                badgeTone = if (summary.delta >= 0) colors.success else colors.error,
                modifier = Modifier.weight(1f)
            )
            M3EAttendanceSummaryCell(
                label = "Total Safe Leaves",
                value = "${summary.totalSafeBunks}",
                sub = "${summary.safeCount} of ${summary.totalCourses} courses safe",
                accent = colors.success,
                badge = "classes",
                badgeTone = colors.success,
                modifier = Modifier.weight(1f)
            )
            M3EAttendanceSummaryCell(
                label = "Courses At Risk",
                value = "${summary.atRiskCount}",
                sub = if (summary.atRiskCount == 0) {
                    "All courses compliant"
                } else {
                    "Below ${state.targetPercent}%"
                },
                accent = riskTone,
                badge = if (summary.atRiskCount > 0) "under ${state.targetPercent}%" else null,
                badgeTone = colors.error,
                modifier = Modifier.weight(1f)
            )
            M3EAttendanceSummaryCell(
                label = "Remaining Days",
                value = "${summary.remainingDays}",
                sub = "working days",
                accent = colors.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun M3EAttendanceSummaryCell(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    badge: String? = null,
    badgeTone: Color = accent,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Column(
        modifier = modifier.widthIn(min = 140.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
            letterSpacing = 1.sp,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                maxLines = 1
            )
            if (!badge.isNullOrBlank()) {
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeTone.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTone,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = sub,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = colors.onSurfaceVariant,
            maxLines = 2
        )
    }
}

/** Explains the lock that currently bounds the projection, using real dates. */
private fun lockExplainer(state: M3EAttendancePredictorState): String {
    val dates = state.milestoneDates
    return when (state.mode) {
        "CAT1", "CAT2" -> {
            val exam = if (state.mode == "CAT1") dates.cat1Date else dates.cat2Date
            val name = if (state.mode == "CAT1") "CAT I" else "CAT II"
            "Attendance freezes $CAT_LOCK_OFFSET_DAYS days before $name — " +
                "${formatCatLockDate(exam)}. Classes from that day on are not counted."
        }
        "LID", "ALL" ->
            "Lab attendance locks at LID — Lab (${formatShortDate(dates.lidLabDate)}); " +
                "theory at LID — Theory (${formatShortDate(dates.lidTheoryDate)})."
        else -> "Every scheduled working day is projected."
    }
}

private data class MilestoneOption(
    val id: String,
    val label: String,
    val dateLabel: String?,
    val available: Boolean
)

private fun milestoneOptions(state: M3EAttendancePredictorState): List<MilestoneOption> {
    val dates = state.milestoneDates
    val lidDate = dates.lidTheoryDate ?: dates.lidLabDate
    return listOf(
        MilestoneOption(
            id = "CAT1",
            label = "Till CAT I",
            dateLabel = dates.cat1Date?.let { formatCatLockDate(it) },
            available = dates.cat1Date != null
        ),
        MilestoneOption(
            id = "CAT2",
            label = "Till CAT II",
            dateLabel = dates.cat2Date?.let { formatCatLockDate(it) },
            available = dates.cat2Date != null
        ),
        MilestoneOption(
            id = "LID",
            label = "Till LID",
            dateLabel = lidDate?.let { formatShortDate(it) },
            available = lidDate != null
        ),
        MilestoneOption(id = "ALL", label = "All Days", dateLabel = null, available = true)
    )
}

/** `5 Aug`, read from a real epoch-millisecond timestamp. */
private fun formatShortDate(epochMillis: Long?): String {
    if (epochMillis == null) return "no data"
    return runCatching {
        val date = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        "${date.dayOfMonth} ${monthAbbrev(date.month.name)}"
    }.getOrDefault("no data")
}

/** The CAT lock is three days *before* the exam, same as the web `lockDateFor`. */
private fun formatCatLockDate(epochMillis: Long?): String {
    if (epochMillis == null) return "no data"
    return runCatching {
        val base = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val locked = kotlinx.datetime.LocalDate.fromEpochDays(base.toEpochDays() - CAT_LOCK_OFFSET_DAYS)
        "${locked.dayOfMonth} ${monthAbbrev(locked.month.name)}"
    }.getOrDefault("no data")
}

private fun monthAbbrev(monthName: String): String =
    monthName.take(3).lowercase().replaceFirstChar { it.uppercase() }

@Preview
@Composable
private fun M3EAttendancePredictorScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EAttendancePredictorScreen(
            state = M3EAttendancePredictorState(),
            onAction = {}
        )
    }
}
