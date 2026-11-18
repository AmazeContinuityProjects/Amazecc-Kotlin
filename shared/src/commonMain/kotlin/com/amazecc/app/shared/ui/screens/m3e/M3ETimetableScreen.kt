package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.domain.overhaul.M3ETimetableAction
import com.amazecc.app.shared.domain.overhaul.M3ETimetableDay
import com.amazecc.app.shared.domain.overhaul.M3ETimetableSlot
import com.amazecc.app.shared.domain.overhaul.M3ETimetableState
import com.amazecc.app.shared.domain.overhaul.TimetableViewMode
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EHorizontalTimetableGrid
import com.amazecc.app.shared.ui.m3e.components.M3EPageShell
import com.amazecc.app.shared.ui.m3e.components.M3EStatItem
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EVerticalTimetableGrid
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Timetable Screen.
 * 100% Stateless: accepts [M3ETimetableState] and emits [M3ETimetableAction].
 * Supports 3 view modes: Day Schedule, Vertical Grid, and Horizontal Grid.
 */
@Composable
fun M3ETimetableScreen(
    state: M3ETimetableState,
    onAction: (M3ETimetableAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val selectedDay = state.days.getOrNull(state.selectedDayIndex)

    val stats = remember(state.totalCredits, state.totalCourses) {
        listOf(
            M3EStatItem(
                id = "credits",
                label = "Credits",
                value = "${state.totalCredits}",
                sub = "Registered",
                tone = M3EStatTone.PRIMARY
            ),
            M3EStatItem(
                id = "courses",
                label = "Courses",
                value = "${state.totalCourses}",
                sub = "Active",
                tone = M3EStatTone.NEUTRAL
            )
        )
    }

    M3EPageShell(
        title = "Timetable",
        eyebrow = "Schedule",
        subtitle = "${state.semesterName} • ${state.totalCredits} Credits Registered",
        stats = stats,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // View Mode Switcher (Schedule vs Vertical Grid vs Horizontal Grid)
            item(key = "view_mode_switcher") {
                M3EConnectedButtonGroup(
                    items = TimetableViewMode.entries,
                    selectedItem = state.viewMode,
                    onItemSelected = { onAction(M3ETimetableAction.SetViewMode(it)) },
                    labelProvider = { it.label }
                )
            }

            when (state.viewMode) {
                TimetableViewMode.SCHEDULE -> {
                    // Day Selector Carousel (Mon - Sat)
                    item(key = "day_selector") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(state.days) { index, day ->
                                val isSelected = index == state.selectedDayIndex
                                val dayBg = if (isSelected) colors.primary else colors.surfaceContainerLow
                                val dayContent = if (isSelected) colors.onPrimary else colors.onSurface

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(dayBg)
                                        .clickable { onAction(M3ETimetableAction.SelectDay(index)) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = day.shortName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = dayContent.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "${day.slots.size} classes",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = dayContent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Empty Day State or Slot List
                    if (selectedDay == null || selectedDay.slots.isEmpty()) {
                        item(key = "empty_day") {
                            M3ESurfaceCard(
                                containerLevel = SurfaceContainerLevel.LOW,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "No classes scheduled for ${selectedDay?.dayName ?: "this day"}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "Enjoy your free day or catch up on coursework!",
                                        fontSize = 12.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = selectedDay.slots,
                            key = { it.id }
                        ) { slot ->
                            M3ETimetableSlotCard(
                                slot = slot,
                                onClick = { onAction(M3ETimetableAction.SlotClicked(slot)) }
                            )
                        }
                    }
                }

                TimetableViewMode.VERTICAL -> {
                    item(key = "vertical_grid") {
                        M3EVerticalTimetableGrid(
                            days = state.days,
                            selectedDayIndex = state.selectedDayIndex,
                            onSelectDay = { onAction(M3ETimetableAction.SelectDay(it)) },
                            onSlotClick = { onAction(M3ETimetableAction.SlotClicked(it)) }
                        )
                    }
                }

                TimetableViewMode.HORIZONTAL -> {
                    item(key = "horizontal_grid") {
                        M3EHorizontalTimetableGrid(
                            days = state.days,
                            onSlotClick = { onAction(M3ETimetableAction.SlotClicked(it)) }
                        )
                    }
                }
            }

            // Bottom Spacing for floating toolbar
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun M3ETimetableSlotCard(
    slot: M3ETimetableSlot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    val cardBorder = if (slot.isHappeningNow) colors.success else null

    M3ESurfaceCard(
        containerLevel = if (slot.isHappeningNow) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
        borderColor = cardBorder,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Row: Slot Pill + Time + Now Badge
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (slot.isLab) colors.secondaryContainer else colors.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = slot.slot,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (slot.isLab) colors.onSecondaryContainer else colors.onPrimaryContainer
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${slot.startTime} - ${slot.endTime}",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                if (slot.isHappeningNow) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.successContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "HAPPENING NOW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.success
                        )
                    }
                }
            }

            // Middle: Course Code + Title
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = slot.courseCode,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    text = slot.courseTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }

            // Bottom: Venue & Faculty
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
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = slot.venue,
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
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = slot.faculty,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun createMockTimetableState() = M3ETimetableState(
    semesterName = "Winter Semester 2025-26",
    selectedDayIndex = 0,
    totalCredits = 24,
    totalCourses = 7,
    days = listOf(
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
)

@Preview
@Composable
private fun M3ETimetableScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ETimetableScreen(state = createMockTimetableState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ETimetableScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ETimetableScreen(state = createMockTimetableState(), onAction = {})
    }
}
