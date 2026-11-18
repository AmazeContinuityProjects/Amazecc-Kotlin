package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            days.forEach { day ->
                M3EWeekDayDisc(
                    day = day,
                    onClick = { onDaySelected(day.date) }
                )
            }
        }
    }
}

@Composable
private fun M3EWeekDayDisc(
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

    val a11yDescription = "${day.dayName} ${day.dayNumber} · " + when {
        day.holidayName != null -> day.holidayName
        day.isHoliday -> "Holiday"
        day.examCount > 0 -> "${day.examCount} Exam${if (day.examCount > 1) "s" else ""}"
        day.hasClasses -> "Classes scheduled"
        else -> "No classes"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = a11yDescription }
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
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

        // Event Dot (Exam, Holiday, or Class indicator)
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
