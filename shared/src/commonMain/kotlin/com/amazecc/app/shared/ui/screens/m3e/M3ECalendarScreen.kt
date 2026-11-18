package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** One classified event on a calendar day. */
@Immutable
data class M3ECalendarEvent(
    val title: String,
    val type: String,
    val category: String = ""
)

/** One day cell of the month grid. */
@Immutable
data class M3ECalendarDay(
    val date: Int,
    val events: List<M3ECalendarEvent> = emptyList(),
    val isToday: Boolean = false,
    val isSelected: Boolean = false
)

/** A month of the academic calendar, mapped from the scraped CalendarMonth. */
@Immutable
data class M3ECalendarMonth(
    val name: String,
    val days: List<M3ECalendarDay> = emptyList(),
    val leadingBlankDays: Int = 0
)

/** Immutable, screen-owned state for [M3ECalendarScreen]. */
@Immutable
data class M3ECalendarState(
    val calendarNames: List<String> = emptyList(),
    val selectedCalendarIndex: Int = 0,
    val months: List<M3ECalendarMonth> = emptyList(),
    val selectedMonthIndex: Int = 0,
    val selectedDay: Int? = null,
    val eventFilter: M3ECalendarFilter = M3ECalendarFilter.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** Coarse event groups that map onto VTOP's event types. */
enum class M3ECalendarFilter(val label: String) {
    ALL("All"),
    INSTRUCTIONAL("Classes"),
    HOLIDAY("Holidays"),
    EXAM("Exams"),
    OTHER("Other")
}

/** User intents emitted by [M3ECalendarScreen]. */
sealed interface M3ECalendarAction {
    data class SelectCalendar(val index: Int) : M3ECalendarAction
    data class SelectMonth(val index: Int) : M3ECalendarAction
    data class SelectDay(val day: Int) : M3ECalendarAction
    data class SetFilter(val filter: M3ECalendarFilter) : M3ECalendarAction
    data object Refresh : M3ECalendarAction
}

/**
 * Material 3 Expressive Academic Calendar screen.
 * 100% stateless: receives [M3ECalendarState] and emits [M3ECalendarAction].
 * Months come from AppState.domain.schedule.calendarsList / AppState.calendar
 * (online scraper / offline cache) through the host bridge.
 */
@Composable
fun M3ECalendarScreen(
    state: M3ECalendarState,
    onAction: (M3ECalendarAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val activeMonth = state.months.getOrNull(state.selectedMonthIndex)
    val selectedDayModel = activeMonth?.days?.firstOrNull { it.date == state.selectedDay }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Academic Calendar",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Instructional days, holidays & exams",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onAction(M3ECalendarAction.Refresh) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh calendar",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (state.isLoading) {
            item(key = "progress") {
                M3EWavyLinearProgress(progress = 0.5f)
            }
        }

        if (state.calendarNames.size > 1) {
            item(key = "calendar_picker") {
                M3EConnectedButtonGroup(
                    items = state.calendarNames,
                    selectedItem = state.calendarNames.getOrNull(state.selectedCalendarIndex) ?: "",
                    onItemSelected = { name ->
                        onAction(M3ECalendarAction.SelectCalendar(state.calendarNames.indexOf(name)))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (state.months.isEmpty()) {
            item(key = "empty") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage ?: "No calendar entries available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item(key = "month_picker") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.months.forEachIndexed { index, month ->
                        val isSelected = index == state.selectedMonthIndex
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) colors.primary else colors.surfaceContainerHigh)
                                .clickable { onAction(M3ECalendarAction.SelectMonth(index)) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = month.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "month_grid") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val leadingBlanks = activeMonth?.leadingBlankDays?.coerceAtLeast(0) ?: 0
                        val cells = (0 until leadingBlanks).map { null } +
                            activeMonth?.days.orEmpty().map { it }
                        cells.chunked(7).forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Spacer(Modifier.weight(1f).height(36.dp))
                                    } else {
                                        val hasEvents = day.events.isNotEmpty()
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        day.isSelected -> colors.primary
                                                        day.isToday -> colors.primaryContainer
                                                        hasEvents -> colors.surfaceContainerHigh
                                                        else -> colors.surfaceContainerLow
                                                    }
                                                )
                                                .border(
                                                    width = if (day.isToday && !day.isSelected) 1.dp else 0.dp,
                                                    color = colors.primary,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable { onAction(M3ECalendarAction.SelectDay(day.date)) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${day.date}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (day.isSelected) colors.onPrimary else colors.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "filter") {
                M3EConnectedButtonGroup(
                    items = M3ECalendarFilter.entries,
                    selectedItem = state.eventFilter,
                    onItemSelected = { onAction(M3ECalendarAction.SetFilter(it)) },
                    labelProvider = { it.label },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val filteredEvents = selectedDayModel?.events?.filter { event ->
                when (state.eventFilter) {
                    M3ECalendarFilter.ALL -> true
                    M3ECalendarFilter.INSTRUCTIONAL -> event.type.contains("instructional", true) ||
                        event.category.contains("class", true)
                    M3ECalendarFilter.HOLIDAY -> event.type.contains("holiday", true) ||
                        event.category.contains("holiday", true)
                    M3ECalendarFilter.EXAM -> event.type.contains("exam", true) ||
                        event.category.contains("exam", true)
                    M3ECalendarFilter.OTHER -> !event.type.contains("instructional", true) &&
                        !event.type.contains("holiday", true) &&
                        !event.type.contains("exam", true)
                }
            }.orEmpty()

            item(key = "day_header") {
                Text(
                    text = if (state.selectedDay == null) {
                        "Select a day to see events"
                    } else {
                        "Events on day ${state.selectedDay} · ${filteredEvents.size}"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
            }

            if (filteredEvents.isEmpty()) {
                item(key = "no_events") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW
                    ) {
                        Text(
                            text = "Nothing scheduled for this day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredEvents, key = { "${it.type}_${it.title}" }) { event ->
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface
                                )
                                if (event.type.isNotBlank()) {
                                    Text(
                                        text = event.type,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(BOTTOM_NAV_PADDING))
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

private fun previewCalendarState() = M3ECalendarState(
    calendarNames = listOf("Fall 2026-27", "Winter 2026-27"),
    months = listOf(
        M3ECalendarMonth(
            name = "October 2026",
            leadingBlankDays = 3,
            days = listOf(
                M3ECalendarDay(1, listOf(M3ECalendarEvent("Instructional Day", "Instructional Day")), isToday = false, isSelected = false),
                M3ECalendarDay(4, listOf(M3ECalendarEvent("CAT-1", "Exam"), M3ECalendarEvent("No class", "Holiday")), isToday = true, isSelected = true)
            )
        )
    ),
    selectedMonthIndex = 0,
    selectedDay = 4
)

@Preview
@Composable
private fun M3ECalendarScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECalendarScreen(state = previewCalendarState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ECalendarScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECalendarScreen(state = previewCalendarState(), onAction = {})
    }
}
