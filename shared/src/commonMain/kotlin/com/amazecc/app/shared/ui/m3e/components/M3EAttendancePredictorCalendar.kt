package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorState
import com.amazecc.app.shared.domain.overhaul.M3EPredictorCalendarDay
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-139a: the interactive, stateless whole-day simulator for the attendance
 * predictor. Mirrors the web "Interactive Day Simulator" section: a month
 * switcher, one tile per working day, and an honest empty state.
 *
 * A tile cycles present (0) -> absent (1) -> off (2) -> present by emitting
 * [M3EAttendancePredictorAction.ToggleDayState] with the real epoch day. All
 * numbers on the tile come from [M3EPredictorCalendarDay.classCount]; nothing
 * is invented here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun M3EAttendancePredictorCalendar(
    state: M3EAttendancePredictorState,
    onAction: (M3EAttendancePredictorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    // Month labels are derived from the data the reducer supplied, in order of
    // first appearance — the same "monthsAvailable" idea as the web component,
    // but without duplicating a month list in immutable state.
    val months = remember(state.calendarDays) {
        state.calendarDays.map { it.monthLabel }.distinct()
    }
    val index = state.monthIndex.coerceIn(0, (months.size - 1).coerceAtLeast(0))
    val monthLabel = months.getOrNull(index) ?: "the selected month"
    val days = remember(state.calendarDays, monthLabel) {
        state.calendarDays.filter { it.monthLabel == monthLabel }
    }

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 24.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Section heading + the whole-day vs per-subject explanation.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Interactive Day Simulator ($monthLabel)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = "Whole day, every subject: Present → Absent (Bunk) → Off (Holiday). " +
                    "To miss one subject only, open it from the list below.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = colors.onSurfaceVariant
            )

            // Month switcher: both directions are emitted clamped, so an
            // out-of-range index from state can never walk past the data.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onAction(M3EAttendancePredictorAction.SetMonthIndex((index - 1).coerceAtLeast(0))) },
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous month",
                        tint = if (index > 0) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = monthLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 76.dp)
                )
                IconButton(
                    onClick = {
                        onAction(
                            M3EAttendancePredictorAction.SetMonthIndex(
                                (index + 1).coerceAtMost((months.size - 1).coerceAtLeast(0))
                            )
                        )
                    },
                    enabled = index < months.lastIndex,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next month",
                        tint = if (index < months.lastIndex) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (days.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No working days scheduled in $monthLabel for the selected milestone.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // FlowRow instead of a nested grid: the parent screen already
                // scrolls, so the tiles must not introduce a second scroller.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    days.forEach { day ->
                        // dayStates is the simulation override map; the day's
                        // own state is pre-computed by the reducer. Prefer an
                        // explicit override, fall back to the derived field.
                        val dayState = state.dayStates[day.date] ?: day.state
                        M3EAttendanceDayTile(
                            day = day,
                            dayState = dayState,
                            onClick = { onAction(M3EAttendancePredictorAction.ToggleDayState(day.date)) }
                        )
                    }
                }
            }

            // Legend — uses the same M3E tokens as the tiles above.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendItem(label = "Present / Attending", swatch = colors.primary)
                LegendItem(label = "Absent / Bunked", swatch = colors.error)
                LegendItem(label = "Off / Excluded", swatch = colors.surfaceContainerHigh)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: Color, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(swatch)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun M3EAttendanceDayTile(
    day: M3EPredictorCalendarDay,
    dayState: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val isAbsent = dayState == 1
    val isOff = dayState == 2

    val container = when {
        isAbsent -> colors.error
        isOff -> colors.surfaceContainerHigh
        day.isToday -> colors.primary
        else -> colors.surfaceContainerLow
    }
    val foreground = when {
        isAbsent -> colors.onError
        isOff -> colors.onSurfaceVariant
        day.isToday -> colors.onPrimary
        else -> colors.onSurface
    }
    val badgeColor = when {
        isAbsent -> colors.error
        isOff -> colors.surfaceContainerHighest
        day.isToday -> colors.primary
        else -> colors.surfaceContainer
    }
    val badgeTextColor = when {
        isAbsent -> colors.onError
        isOff -> colors.onSurfaceVariant
        day.isToday -> colors.onPrimary
        else -> colors.onSurfaceVariant
    }
    val label = when {
        isAbsent -> "Absent"
        isOff -> "Off"
        else -> "${day.classCount} cl"
    }
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .width(78.dp)
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(container)
            .then(
                if (isOff) {
                    // Off days read as "excluded": dashed outline, muted fill.
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = colors.outline,
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                                    0f
                                )
                            )
                        )
                    }
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = if (day.isToday || isAbsent) container else colors.outlineVariant,
                        shape = shape
                    )
                }
            )
            .clickableA11y(
                onClickLabel = "Toggle attendance for ${day.weekdayShort} ${day.monthLabel} ${day.dayNumber}"
            ) { onClick() }
            .semantics { selected = isAbsent }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = day.weekdayShort.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
            maxLines = 1
        )
        Text(
            text = "${day.dayNumber}",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = foreground,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = badgeTextColor,
                maxLines = 1
            )
        }
    }
}

@Preview
@Composable
private fun M3EAttendancePredictorCalendarPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EAttendancePredictorCalendar(
            state = com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorState(),
            onAction = {}
        )
    }
}
