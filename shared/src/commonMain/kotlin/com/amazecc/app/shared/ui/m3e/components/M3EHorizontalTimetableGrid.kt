package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ETimetableDay
import com.amazecc.app.shared.domain.overhaul.M3ETimetableSlot
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Period model representing columns across the timetable grid.
 */
data class TimetablePeriodColumn(
    val id: String,
    val timeLabel: String,
    val startTime: String,
    val endTime: String,
    val startMinute: Int,
    val endMinute: Int,
    val isLunch: Boolean = false
)

val StandardTimetablePeriods = listOf(
    TimetablePeriodColumn("p1", "08:00 - 08:50", "08:00", "08:50", 480, 530),
    TimetablePeriodColumn("p2", "09:00 - 09:50", "09:00", "09:50", 540, 590),
    TimetablePeriodColumn("p3", "10:00 - 10:50", "10:00", "10:50", 600, 650),
    TimetablePeriodColumn("p4", "11:00 - 11:50", "11:00", "11:50", 660, 710),
    TimetablePeriodColumn("p5", "11:50 - 12:40", "11:50", "12:40", 710, 760),
    TimetablePeriodColumn("lunch", "12:40 - 13:30", "12:40", "13:30", 760, 810, isLunch = true),
    TimetablePeriodColumn("p6", "14:00 - 14:50", "14:00", "14:50", 840, 890),
    TimetablePeriodColumn("p7", "15:00 - 15:50", "15:00", "15:50", 900, 950),
    TimetablePeriodColumn("p8", "16:00 - 16:50", "16:00", "16:50", 960, 1010),
    TimetablePeriodColumn("p9", "17:00 - 17:50", "17:00", "17:50", 1020, 1070),
    TimetablePeriodColumn("p10", "18:00 - 18:50", "18:00", "18:50", 1080, 1130),
    TimetablePeriodColumn("p11", "19:00 - 19:50", "19:00", "19:50", 1140, 1190)
)

val StandardDaySlotsMap = mapOf(
    "MON" to listOf("A1 / L1", "F1 / L2", "D1 / L3", "TB1 / L4", "TG1 / L5", "LUNCH", "A2 / L31", "F2 / L32", "D2 / L33", "TB2 / L34", "TG2 / L35", "S3 / L36"),
    "TUE" to listOf("B1 / L7", "G1 / L8", "E1 / L9", "TC1 / L10", "TAA1 / L11", "LUNCH", "B2 / L37", "G2 / L38", "E2 / L39", "TC2 / L40", "TAA2 / L41", "S1 / L42"),
    "WED" to listOf("C1 / L13", "A1 / L14", "F1 / L15", "TD1 / L16", "TBB1 / L17", "LUNCH", "C2 / L43", "A2 / L44", "F2 / L45", "TD2 / L46", "TBB2 / L47", "S4 / L48"),
    "THU" to listOf("D1 / L19", "B1 / L20", "G1 / L21", "TE1 / L22", "TCC1 / L23", "LUNCH", "D2 / L49", "B2 / L50", "G2 / L51", "TE2 / L52", "TCC2 / L53", "S2 / L54"),
    "FRI" to listOf("E1 / L25", "C1 / L26", "TA1 / L27", "TF1 / L28", "TDD1 / L29", "LUNCH", "E2 / L55", "C2 / L56", "TA2 / L57", "TF2 / L58", "TDD2 / L59", "S15 / L60"),
    "SAT" to listOf("X11 / L71", "X12 / L72", "Y11 / L73", "Y12 / L74", "S8 / L75", "LUNCH", "X21 / L77", "Z21 / L78", "Y21 / L79", "W21 / L80", "W22 / L81", "Z22 / L82")
)

/**
 * Mobile-friendly Material 3 Expressive horizontally scrollable timetable grid.
 *
 * - Days as rows on the left column (MON, TUE, WED, THU, FRI, SAT)
 * - Time slots as columns across the top (08:00 - 08:50, ..., [LUNCH 12:40 - 13:30], ..., 19:00 - 19:50)
 * - Horizontal scrolling via [Modifier.horizontalScroll]
 * - Course slot card with courseCode, slot code, venue, tonal background, clickable
 * - Free slot cell with subtle outline and default slot code
 * - Lunch break column/cell with restaurant icon
 */
@Composable
fun M3EHorizontalTimetableGrid(
    days: List<M3ETimetableDay>,
    onSlotClick: (M3ETimetableSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val standardDays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")

    // Normalize display days to cover Monday through Saturday
    val displayDays = remember(days) {
        standardDays.map { dayKey ->
            days.find {
                it.shortName.equals(dayKey, ignoreCase = true) ||
                    it.dayName.take(3).equals(dayKey, ignoreCase = true)
            } ?: M3ETimetableDay(
                dayName = when (dayKey) {
                    "MON" -> "Monday"
                    "TUE" -> "Tuesday"
                    "WED" -> "Wednesday"
                    "THU" -> "Thursday"
                    "FRI" -> "Friday"
                    "SAT" -> "Saturday"
                    else -> dayKey
                },
                shortName = dayKey,
                dayNumber = standardDays.indexOf(dayKey) + 1,
                isToday = false,
                isSelected = false,
                slots = emptyList()
            )
        }
    }

    val dayColumnWidth = 60.dp
    val slotCellWidth = 114.dp
    val lunchCellWidth = 68.dp
    val headerHeight = 44.dp
    val rowHeight = 74.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Pinned Left Column (Days)
            Column(
                modifier = Modifier.width(dayColumnWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header cell for Days
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant
                    )
                }

                // Day Labels
                displayDays.forEach { day ->
                    val isToday = day.isToday
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isToday) colors.primary else colors.surfaceContainer)
                            .border(
                                1.dp,
                                if (isToday) colors.primary else colors.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = day.shortName.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) colors.onPrimary else colors.onSurface
                            )
                            if (day.slots.isNotEmpty()) {
                                Text(
                                    text = "${day.slots.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isToday) colors.onPrimary.copy(alpha = 0.8f) else colors.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Horizontally Scrollable Grid Area (Periods & Slot Cells)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Row (Periods)
                Row(
                    modifier = Modifier.height(headerHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StandardTimetablePeriods.forEach { period ->
                        val cellW = if (period.isLunch) lunchCellWidth else slotCellWidth
                        Box(
                            modifier = Modifier
                                .width(cellW)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (period.isLunch) colors.surfaceContainerHighest else colors.surfaceContainer)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (period.isLunch) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restaurant,
                                        contentDescription = "Lunch Break",
                                        tint = colors.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "12:40",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = period.timeLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }

                // Grid Rows (One per Day)
                displayDays.forEach { day ->
                    val dayCode = day.shortName.uppercase()
                    val matchedIdsForDay = mutableSetOf<String>()
                    Row(
                        modifier = Modifier.height(rowHeight),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StandardTimetablePeriods.forEachIndexed { periodIndex, period ->
                            val defaultCode = StandardDaySlotsMap[dayCode]?.getOrNull(periodIndex) ?: ""
                            val cellW = if (period.isLunch) lunchCellWidth else slotCellWidth

                            if (period.isLunch) {
                                // Lunch Break Divider Cell
                                Box(
                                    modifier = Modifier
                                        .width(cellW)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceContainerLowest)
                                        .border(
                                            1.dp,
                                            colors.outlineVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Restaurant,
                                            contentDescription = "Lunch Break",
                                            tint = colors.tertiary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Lunch",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            } else {
                                // Check if a slot is present
                                val availableSlots = day.slots.filter { it.id !in matchedIdsForDay }
                                val matchedSlot = findMatchingSlot(availableSlots, dayCode, periodIndex, period)
                                if (matchedSlot != null) {
                                    matchedIdsForDay.add(matchedSlot.id)
                                }

                                if (matchedSlot != null) {
                                    // Filled Course Slot Card
                                    val isNow = matchedSlot.isHappeningNow
                                    val bg = when {
                                        isNow -> colors.successContainer.copy(alpha = 0.35f)
                                        matchedSlot.isLab -> colors.secondaryContainer.copy(alpha = 0.45f)
                                        else -> colors.surfaceContainerHigh
                                    }
                                    val borderColor = if (isNow) colors.success else colors.outlineVariant.copy(alpha = 0.5f)

                                    Box(
                                        modifier = Modifier
                                            .width(cellW)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(bg)
                                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                            .clickable { onSlotClick(matchedSlot) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxHeight(),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = matchedSlot.courseCode,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isNow) colors.success else colors.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = matchedSlot.slot,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = matchedSlot.venue,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    // Free Slot Cell (Subtle outlined / surfaceContainerLowest)
                                    Box(
                                        modifier = Modifier
                                            .width(cellW)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.surfaceContainerLowest)
                                            .border(
                                                1.dp,
                                                colors.outlineVariant.copy(alpha = 0.25f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = defaultCode,
                                            fontSize = 10.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                                            fontWeight = FontWeight.Normal,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Robust matching logic by slot token and time overlap.
 */
internal fun findMatchingSlot(
    slots: List<M3ETimetableSlot>,
    dayCode: String,
    periodIndex: Int,
    period: TimetablePeriodColumn
): M3ETimetableSlot? {
    // 1. Match by scheduled time window first
    val matchByTime = slots.firstOrNull { slot ->
        val sMin = parseTimeToMinutes(slot.startTime)
        val eMin = parseTimeToMinutes(slot.endTime)
        if (sMin != null && eMin != null) {
            maxOf(sMin, period.startMinute) < minOf(eMin, period.endMinute)
        } else if (sMin != null) {
            sMin in (period.startMinute - 15)..(period.startMinute + 25)
        } else {
            false
        }
    }
    if (matchByTime != null) return matchByTime

    // 2. Fallback to slot code token match (e.g. "A1" in "A1 / L1")
    val defaultTokens = (StandardDaySlotsMap[dayCode]?.getOrNull(periodIndex) ?: "")
        .split('/', '+', ' ')
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() && it != "LUNCH" }

    return slots.firstOrNull { slot ->
        val slotParts = slot.slot.uppercase().split('+', '/', ' ').map { it.trim() }
        slotParts.any { it in defaultTokens }
    }
}

internal fun parseTimeToMinutes(timeStr: String): Int? {
    val clean = timeStr.trim().removeSuffix("AM").removeSuffix("PM").removeSuffix("am").removeSuffix("pm").trim()
    val parts = clean.split(":")
    if (parts.size < 2) return null
    val hours = parts[0].trim().toIntOrNull() ?: return null
    val minutes = parts[1].trim().toIntOrNull() ?: return null
    val isPm = timeStr.contains("PM", ignoreCase = true) && hours < 12
    val adjustedHours = if (isPm) hours + 12 else hours
    return adjustedHours * 60 + minutes
}

private fun createSampleGridDays() = listOf(
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
private fun M3EHorizontalTimetableGridPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EHorizontalTimetableGrid(days = createSampleGridDays(), onSlotClick = {})
    }
}

@Preview
@Composable
private fun M3EHorizontalTimetableGridDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EHorizontalTimetableGrid(days = createSampleGridDays(), onSlotClick = {})
    }
}
