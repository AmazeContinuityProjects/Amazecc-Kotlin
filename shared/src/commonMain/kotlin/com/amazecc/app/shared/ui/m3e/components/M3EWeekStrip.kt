package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EHomeWeekDays
import com.amazecc.app.shared.domain.overhaul.M3EWeekDay
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun M3EWeekStrip(
    days: List<M3EWeekDay>,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // D-128: no pill plate behind the row — the discs sit on the page. Seven
    // equal-width discs share the available width, so none can be squeezed or
    // mangled on a phone, and classless weekends are dropped before drawing.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        M3EHomeWeekDays.visibleInStrip(days).forEach { day ->
            M3EWeekDayDisc(
                day = day,
                onClick = { onDaySelected(day.date) }
            )
        }
    }
}

@Composable
private fun RowScope.M3EWeekDayDisc(
    day: M3EWeekDay,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = M3ETheme.motion.spatialFast(),
        label = "DayDiscScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            day.isSelected -> colors.primary
            day.isToday -> colors.surfaceContainerHighest
            else -> Color.Transparent
        },
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "DayDiscBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            day.isSelected -> colors.onPrimary
            day.isToday -> colors.primary
            day.isHoliday -> colors.tertiary
            else -> colors.onSurface
        },
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "DayDiscContent"
    )

    val dayState = when {
        day.holidayName != null -> day.holidayName
        day.isHoliday -> "Holiday"
        day.examCount > 0 -> "${day.examCount} Exam${if (day.examCount > 1) "s" else ""}"
        day.hasClasses -> "Classes scheduled"
        else -> "No classes"
    }
    // A deadline is an extra hint, not a day kind: it never replaces the primary
    // state, so TalkBack still says what the day is and adds that work is due.
    val a11yDescription = "${day.dayName} ${day.dayNumber} · $dayState" +
        if (day.hasDeadline) ", assignment due" else ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            // D-128: weighted discs split the row evenly; a fixed 48dp minimum
            // width used to overflow seven-across on a phone and squeeze the last
            // disc. Only the height keeps a 48dp touch target.
            .weight(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (day.isToday && !day.isSelected) {
                    Modifier.border(1.5.dp, colors.primary, CircleShape)
                } else Modifier
            )
            // D-119: explicit role + label + keyboard activation. The rich day
            // description is the click label so screen readers announce both the
            // action and the day's state.
            .clickableA11y(
                onClickLabel = a11yDescription,
                interactionSource = interactionSource,
                onClick = onClick
            )
            .sizeIn(minWidth = 0.dp, minHeight = 48.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = day.dayName.take(3),
            fontSize = 11.sp,
            color = contentColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = day.dayNumber.toString(),
            fontSize = 14.sp,
            color = contentColor,
            fontWeight = if (day.isSelected || day.isToday) FontWeight.Bold else FontWeight.SemiBold
        )

        // Event Dot (Exam, Holiday, Class, or assignment-deadline indicator)
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        day.isSelected -> colors.onPrimary
                        day.examCount > 0 -> colors.warning
                        day.isHoliday -> colors.tertiary
                        day.hasClasses -> colors.primary
                        day.hasDeadline -> colors.secondary
                        else -> Color.Transparent
                    }
                )
        )
    }
}

@Preview
@Composable
private fun M3EWeekStripPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EWeekStrip(
            days = listOf(
                M3EWeekDay(
                    date = LocalDate(2026, 9, 28),
                    dayName = "Mon",
                    dayNumber = 28,
                    isToday = false,
                    isSelected = false,
                    hasClasses = true,
                    isHoliday = false
                ),
                M3EWeekDay(
                    date = LocalDate(2026, 9, 29),
                    dayName = "Tue",
                    dayNumber = 29,
                    isToday = true,
                    isSelected = true,
                    hasClasses = true,
                    isHoliday = false
                ),
                M3EWeekDay(
                    date = LocalDate(2026, 9, 30),
                    dayName = "Wed",
                    dayNumber = 30,
                    isToday = false,
                    isSelected = false,
                    hasClasses = false,
                    isHoliday = true
                )
            ),
            onDaySelected = {}
        )
    }
}
