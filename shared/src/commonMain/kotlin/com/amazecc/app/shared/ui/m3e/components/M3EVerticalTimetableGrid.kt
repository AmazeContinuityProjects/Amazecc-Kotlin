package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ETimetableDay
import com.amazecc.app.shared.domain.overhaul.M3ETimetableSlot
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Mobile-first Material 3 Expressive vertical timetable grid:
 * - Day selector chip row across the top
 * - Time bands down the vertical axis (08:00 to 19:50)
 * - Shows classes positioned by time with start/end time badge, course code, course title, faculty, and venue
 * - Lunch period indicator banner between 12:40 and 13:30
 * - Honest status indicators derived from real slot times (ACTIVE / COMPLETED /
 *   UPCOMING / neutral), never a blanket "UPCOMING" for finished classes
 * - Clickable slot items triggering [onSlotClick]
 *
 * Density is opt-in: by default this renders a compact, readable list of the real
 * classes for the selected day. Pass [denseGrid] = true for the 12-band timeline.
 */
@Composable
fun M3EVerticalTimetableGrid(
    days: List<M3ETimetableDay>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    onSlotClick: (M3ETimetableSlot) -> Unit,
    modifier: Modifier = Modifier,
    denseGrid: Boolean = false
) {
    if (!denseGrid) {
        M3EVerticalCompactList(
            days = days,
            selectedDayIndex = selectedDayIndex,
            onSelectDay = onSelectDay,
            onSlotClick = onSlotClick,
            modifier = modifier
        )
        return
    }

    val colors = M3ETheme.colors
    val selectedDay = days.getOrNull(selectedDayIndex) ?: days.firstOrNull()
    val nowMinutes = currentLocalMinutes()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Day Selector Chip Carousel
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(days) { index, day ->
                val isSelected = index == selectedDayIndex
                val dayBg = if (isSelected) colors.primary else colors.surfaceContainerLow
                val dayContent = if (isSelected) colors.onPrimary else colors.onSurface

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(dayBg)
                        .border(
                            1.dp,
                            if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectDay(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = day.shortName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = dayContent
                        )
                        Text(
                            text = "${day.slots.size} ${if (day.slots.size == 1) "class" else "classes"}",
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = dayContent.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Timeline Down the Vertical Axis
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val matchedSlotIds = mutableSetOf<String>()

            StandardTimetablePeriods.forEach { period ->
                if (period.isLunch) {
                    // Lunch Period Indicator Banner (12:40 - 13:30)
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colors.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restaurant,
                                    contentDescription = "Lunch Break",
                                    tint = colors.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Lunch Break",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "12:40 PM - 01:30 PM",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    val availableSlots = (selectedDay?.slots ?: emptyList()).filter { it.id !in matchedSlotIds }
                    val matchedSlot = findMatchingSlot(availableSlots, period)
                    if (matchedSlot != null) {
                        matchedSlotIds.add(matchedSlot.id)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Time Band Column
                        Column(
                            modifier = Modifier
                                .width(50.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = period.startTime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            Text(
                                text = period.endTime,
                                fontSize = 10.sp,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .weight(1f)
                                    .background(colors.outlineVariant.copy(alpha = 0.35f))
                            )
                        }

                        // Right Card Content
                        if (matchedSlot != null) {
                            val status = timetableSlotStatus(matchedSlot, nowMinutes, selectedDay)
                            val isNow = status == M3ESlotTimeStatus.ACTIVE
                            val statusLabel = status.label()

                            M3ESurfaceCard(
                                containerLevel = if (isNow) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
                                borderColor = if (isNow) colors.success else null,
                                cornerRadius = 16.dp,
                                onClick = { onSlotClick(matchedSlot) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Top Row: Badges & Status Indicator
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (matchedSlot.isLab) colors.secondaryContainer else colors.primaryContainer)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = matchedSlot.slot,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (matchedSlot.isLab) colors.onSecondaryContainer else colors.onPrimaryContainer
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AccessTime,
                                                    contentDescription = null,
                                                    tint = colors.onSurfaceVariant,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "${matchedSlot.startTime} - ${matchedSlot.endTime}",
                                                    fontSize = 11.sp,
                                                    color = colors.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Status Indicator
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isNow) colors.successContainer else colors.surfaceContainerHighest)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isNow) colors.success else colors.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Course Title & Code
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = matchedSlot.courseCode,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary
                                        )
                                        Text(
                                            text = matchedSlot.courseTitle,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface
                                        )
                                    }

                                    // Faculty & Venue
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocationOn,
                                                contentDescription = null,
                                                tint = colors.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = matchedSlot.venue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = colors.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = matchedSlot.faculty,
                                                fontSize = 12.sp,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Subtle Free Period Cell
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceContainerLowest)
                                    .border(
                                        1.dp,
                                        colors.outlineVariant.copy(alpha = 0.25f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "—",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "No class scheduled",
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Additional non-standard slots if any
            val extraSlots = (selectedDay?.slots ?: emptyList()).filter { it.id !in matchedSlotIds }
            extraSlots.forEach { extraSlot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(50.dp)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = extraSlot.startTime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = extraSlot.endTime,
                            fontSize = 10.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        cornerRadius = 16.dp,
                        onClick = { onSlotClick(extraSlot) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = extraSlot.courseCode,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = extraSlot.courseTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = extraSlot.venue,
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = extraSlot.faculty,
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Honest temporal state of a published class. UNKNOWN is never presented as UPCOMING. */
internal enum class M3ESlotTimeStatus { ACTIVE, COMPLETED, UPCOMING, UNKNOWN }

/**
 * The host's [M3ETimetableSlot.isHappeningNow] wins; otherwise derive from real slot times.
 *
 * A slot's [day] must be the one explicitly flagged as today: time-of-day alone cannot
 * distinguish a finished class today from a morning class on a future day. With no day
 * (the safe default, and for every other day) the result is neutral [M3ESlotTimeStatus.UNKNOWN].
 */
internal fun timetableSlotStatus(
    slot: M3ETimetableSlot,
    nowMinutes: Int,
    day: M3ETimetableDay? = null
): M3ESlotTimeStatus {
    if (day?.isToday != true) return M3ESlotTimeStatus.UNKNOWN
    if (slot.isHappeningNow) return M3ESlotTimeStatus.ACTIVE
    val start = parseTimeToMinutes(slot.startTime) ?: return M3ESlotTimeStatus.UNKNOWN
    val end = parseTimeToMinutes(slot.endTime) ?: return M3ESlotTimeStatus.UNKNOWN
    return when {
        nowMinutes > end -> M3ESlotTimeStatus.COMPLETED
        nowMinutes < start -> M3ESlotTimeStatus.UPCOMING
        // Inside the clock window but not flagged active: stay neutral.
        else -> M3ESlotTimeStatus.UNKNOWN
    }
}

internal fun M3ESlotTimeStatus.label(): String = when (this) {
    M3ESlotTimeStatus.ACTIVE -> "ACTIVE"
    M3ESlotTimeStatus.COMPLETED -> "COMPLETED"
    M3ESlotTimeStatus.UPCOMING -> "UPCOMING"
    M3ESlotTimeStatus.UNKNOWN -> "SCHEDULED"
}

internal fun currentLocalMinutes(): Int {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return now.hour * 60 + now.minute
}

/** Default list-first presentation: real classes of the selected day, no invented grid. */
@Composable
private fun M3EVerticalCompactList(
    days: List<M3ETimetableDay>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    onSlotClick: (M3ETimetableSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val selectedDay = days.getOrNull(selectedDayIndex) ?: days.firstOrNull()
    val nowMinutes = currentLocalMinutes()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(days) { index, day ->
                val isSelected = index == selectedDayIndex
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colors.primary else colors.surfaceContainerLow)
                        .border(
                            1.dp,
                            if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectDay(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = day.shortName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) colors.onPrimary else colors.onSurface
                        )
                        Text(
                            text = "${day.slots.size} ${if (day.slots.size == 1) "class" else "classes"}",
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            color = (if (isSelected) colors.onPrimary else colors.onSurface).copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        if (selectedDay == null || selectedDay.slots.isEmpty()) {
            M3ESurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerLevel = SurfaceContainerLevel.LOW
            ) {
                Text(
                    text = "— No classes scheduled",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant
                )
            }
        } else {
            selectedDay.slots.forEach { slot ->
                val status = timetableSlotStatus(slot, nowMinutes, selectedDay)
                val isActive = status == M3ESlotTimeStatus.ACTIVE
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = if (isActive) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
                    borderColor = if (isActive) colors.success else null,
                    onClick = { onSlotClick(slot) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = slot.courseCode,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = slot.courseTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            val meta = listOfNotNull(
                                slot.slot.takeIf { it.isNotBlank() },
                                slot.venue.takeIf { it.isNotBlank() },
                                slot.faculty.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (meta.isNotBlank()) {
                                Text(text = meta, fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = status.label(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) colors.success else colors.onSurfaceVariant
                            )
                            Text(
                                text = verticalSlotTimeRange(slot),
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun verticalSlotTimeRange(slot: M3ETimetableSlot): String =
    if (slot.startTime.isBlank() || slot.endTime.isBlank()) "Time not published"
    else "${slot.startTime} - ${slot.endTime}"

private fun createSampleVerticalDays() = listOf(
    M3ETimetableDay(
        dayName = "Monday",
        shortName = "Mon",
        dayNumber = 1,
        isToday = true,
        isSelected = true,
        slots = listOf(
            M3ETimetableSlot(
                id = "slot_1",
                slot = "A1",
                courseCode = "CSE3002",
                courseTitle = "Internet and Web Programming",
                venue = "SJT 412",
                faculty = "Prof. Sundar",
                startTime = "08:00",
                endTime = "08:50",
                isHappeningNow = false,
                attendanceTone = M3ECardTone.GOOD
            ),
            M3ETimetableSlot(
                id = "slot_2",
                slot = "B1+TB1",
                courseCode = "CSE2005",
                courseTitle = "Operating Systems",
                venue = "TT 314",
                faculty = "Dr. Anita",
                startTime = "09:00",
                endTime = "09:50",
                isHappeningNow = true,
                attendanceTone = M3ECardTone.WARNING
            )
        )
    ),
    M3ETimetableDay(
        dayName = "Tuesday",
        shortName = "Tue",
        dayNumber = 2,
        isToday = false,
        isSelected = false,
        slots = emptyList()
    )
)

@Preview
@Composable
private fun M3EVerticalTimetableGridPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EVerticalTimetableGrid(
            days = createSampleVerticalDays(),
            selectedDayIndex = 0,
            onSelectDay = {},
            onSlotClick = {}
        )
    }
}

@Preview
@Composable
private fun M3EVerticalTimetableGridDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EVerticalTimetableGrid(
            days = createSampleVerticalDays(),
            selectedDayIndex = 0,
            onSelectDay = {},
            onSlotClick = {}
        )
    }
}

@Preview
@Composable
private fun M3EVerticalTimetableGridDensePreview() {
    AmazeM3ETheme(isDark = false) {
        M3EVerticalTimetableGrid(
            days = createSampleVerticalDays(),
            selectedDayIndex = 0,
            onSelectDay = {},
            onSlotClick = {},
            denseGrid = true
        )
    }
}
