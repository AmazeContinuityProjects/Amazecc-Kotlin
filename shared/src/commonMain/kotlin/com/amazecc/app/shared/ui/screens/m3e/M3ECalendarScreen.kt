package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EPageAction
import com.amazecc.app.shared.ui.m3e.components.M3EPageActionsMenu
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The real source that put an entry on a calendar day.
 *
 * These are the web `EventKind` categories the super calendar merges: the VTOP academic calendar
 * (holidays / day types / milestones), the exam schedule, Moodle deadlines, the task store, OD
 * records, attendance classes and the user's own EventHub registrations. Keeping the source on the
 * entry (rather than sniffing its title) is what lets a dot, a filter and a label agree.
 */
enum class M3ECalendarSource {
    CLASS, EXAM, MILESTONE, DEADLINE, HOLIDAY, OD, EVENT, DAY_TYPE, OTHER
}

/** One classified event on a calendar day. */
@Immutable
data class M3ECalendarEvent(
    val title: String,
    val type: String,
    val category: String = "",
    /**
     * The source that produced this entry. `null` only for raw/legacy entries created before the
     * enriched projection was wired in; [effectiveSource] infers one from their text in that case.
     */
    val source: M3ECalendarSource? = null
)

/**
 * The classified day type carried through from the domain projection.
 *
 * D-113's tint used to be derived by sniffing event text. Carrying the real type keeps that tint
 * correct now that a day can hold an exam, a deadline and a class at once — none of which should
 * change what the college published about the day itself.
 */
enum class M3ECalendarDayType { INSTRUCTIONAL, SEMIHOLIDAY, NON_INSTRUCTIONAL, HOLIDAY, OTHER }

/** One day cell of the month grid. */
@Immutable
data class M3ECalendarDay(
    val date: Int,
    val events: List<M3ECalendarEvent> = emptyList(),
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val dayType: M3ECalendarDayType? = null
)

/** A month of the merged super calendar, mapped from the enriched domain projection. */
@Immutable
data class M3ECalendarMonth(
    val name: String,
    val days: List<M3ECalendarDay> = emptyList(),
    val leadingBlankDays: Int = 0
)

/** Semantics hooks used by the headless JVM calendar UI tests. */
object M3ECalendarTestTags {
    const val WEEK_ROW = "m3e_calendar_week_row"
    /** Applied to every grid cell — real day or trailing/leading blank. */
    const val CELL = "m3e_calendar_cell"
    const val SOURCE_LEGEND = "m3e_calendar_source_legend"
    const val EVENT_ROW = "m3e_calendar_event_row"
}

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
    val errorMessage: String? = null,
    /** Feedback after an ICS export (e.g. "Saved to Downloads"). */
    val exportMessage: String? = null
)

/**
 * Per-source filters for the merged super calendar.
 *
 * The web page tints the day by its college-published type but filters the *list* by source, so the
 * two answers stay independent: "is this a teaching day" and "what is on it". These chips follow
 * that split — a day can be instructional and still have its deadline hidden by the Deadlines chip.
 */
enum class M3ECalendarFilter(val label: String) {
    ALL("All"),
    CLASSES("Classes"),
    EXAMS("Exams"),
    DEADLINES("Deadlines"),
    HOLIDAYS("Holidays"),
    EVENTS("Events & OD")
}

/** User intents emitted by [M3ECalendarScreen]. */
sealed interface M3ECalendarAction {
    data class SelectCalendar(val index: Int) : M3ECalendarAction
    data class SelectMonth(val index: Int) : M3ECalendarAction
    data class SelectDay(val day: Int) : M3ECalendarAction
    data class SetFilter(val filter: M3ECalendarFilter) : M3ECalendarAction
    data object Refresh : M3ECalendarAction
    // Web parity — CalendarSubpage actions (D-061):
    data object ExportCalendar : M3ECalendarAction
    data object OpenMoodle : M3ECalendarAction
    data object OpenCirculars : M3ECalendarAction
    data object Today : M3ECalendarAction
}

/**
 * Material 3 Expressive Super Calendar screen.
 * 100% stateless: receives [M3ECalendarState] and emits [M3ECalendarAction].
 * Months are the enriched projection built by the host bridge: the published academic calendar
 * merged with exam papers, Moodle deadlines, tasks, OD records, attendance classes and EventHub
 * registrations. Each entry keeps its [M3ECalendarSource] so the grid dots, the legend, the filter
 * chips and the day list all name the same source.
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
    // D-116: the five crowded header controls are now one M3E action-menu FAB.
    // The menu owns only its open/closed flag; every action lambda stays here.

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Super Calendar",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = colors.onSurface
                        )
                        Text(
                            text = "Classes, exams, deadlines & events",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                    M3EPageActionsMenu(
                        actions = listOf(
                            M3EPageAction(
                                label = "Refresh calendar",
                                icon = Icons.Rounded.Refresh,
                                onClick = { onAction(M3ECalendarAction.Refresh) }
                            ),
                            M3EPageAction(
                                label = "Export semester to .ics",
                                icon = Icons.Rounded.Download,
                                onClick = { onAction(M3ECalendarAction.ExportCalendar) }
                            ),
                            M3EPageAction(
                                label = "Jump to today",
                                icon = Icons.Rounded.DateRange,
                                onClick = { onAction(M3ECalendarAction.Today) }
                            ),
                            M3EPageAction(
                                label = "Connect Moodle",
                                icon = Icons.Rounded.MenuBook,
                                onClick = { onAction(M3ECalendarAction.OpenMoodle) }
                            ),
                            M3EPageAction(
                                label = "Academic circulars",
                                icon = Icons.Rounded.Folder,
                                onClick = { onAction(M3ECalendarAction.OpenCirculars) }
                            )
                        ),
                        collapsedLabel = "More calendar actions",
                        expandedLabel = "Close calendar actions"
                    )
                }
                if (!state.exportMessage.isNullOrBlank()) {
                    Text(
                        text = state.exportMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (state.isLoading) {
            item(key = "progress") {
                M3EWavyLinearProgress(progress = com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE)
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
                                .clickableA11y(
                                    onClickLabel = "Select ${month.name}",
                                    onClick = { onAction(M3ECalendarAction.SelectMonth(index)) }
                                )
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
                        val cells = buildCalendarCells(
                            leadingBlanks = activeMonth?.leadingBlankDays ?: 0,
                            days = activeMonth?.days.orEmpty()
                        )
                        cells.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(M3ECalendarTestTags.WEEK_ROW)
                            ) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Spacer(
                                            Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .testTag(M3ECalendarTestTags.CELL)
                                        )
                                    } else {
                                        CalendarDayCell(
                                            day = day,
                                            isSelected = state.selectedDay == day.date,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onAction(M3ECalendarAction.SelectDay(day.date)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "source_legend") {
                // The web page ships a legend alongside the grid. Here it is derived from the
                // sources actually present this month, so it never promises a dot nothing draws.
                val presentSources = activeMonth?.days.orEmpty()
                    .asSequence()
                    .flatMap { it.events.asSequence() }
                    .map { it.effectiveSource() }
                    .filter { it != M3ECalendarSource.DAY_TYPE && it != M3ECalendarSource.OTHER }
                    .distinct()
                    .toList()
                if (presentSources.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(M3ECalendarTestTags.SOURCE_LEGEND),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        presentSources.forEach { source ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(sourceDotColor(source, colors))
                                )
                                Text(
                                    text = sourceLabel(source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
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
                val source = event.effectiveSource()
                when (state.eventFilter) {
                    M3ECalendarFilter.ALL -> true
                    // The published day type is part of "Classes": it says whether teaching runs,
                    // which is the same answer the web page's Classes chip gives.
                    M3ECalendarFilter.CLASSES ->
                        source == M3ECalendarSource.CLASS || source == M3ECalendarSource.DAY_TYPE
                    // A milestone (CAT / LID / Mid Term) and its papered exam are one assessment,
                    // matching the web page where both pass the Exams chip.
                    M3ECalendarFilter.EXAMS ->
                        source == M3ECalendarSource.EXAM || source == M3ECalendarSource.MILESTONE
                    M3ECalendarFilter.DEADLINES -> source == M3ECalendarSource.DEADLINE
                    // A published "No Instructional Day" is filed with holidays, as on the web.
                    M3ECalendarFilter.HOLIDAYS ->
                        source == M3ECalendarSource.HOLIDAY ||
                            (source == M3ECalendarSource.DAY_TYPE && isNonInstructionalEvent(event))
                    M3ECalendarFilter.EVENTS ->
                        source == M3ECalendarSource.EVENT || source == M3ECalendarSource.OD
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
                items(filteredEvents, key = { "${it.source}_${it.type}_${it.title}" }) { event ->
                    val source = event.effectiveSource()
                    M3ESurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(M3ECalendarTestTags.EVENT_ROW),
                        containerLevel = SurfaceContainerLevel.LOW
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(sourceDotColor(source, colors))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Source label leads the second line so an exam, a Moodle deadline
                                // and a task never read as the same kind of obligation.
                                val secondary = listOfNotNull(
                                    sourceLabel(source),
                                    event.category.takeIf { it.isNotBlank() }
                                ).joinToString(" · ")
                                Text(
                                    text = secondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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

/**
 * One M3E calendar day: springy selection morph, emphasized date and semantic event dots.
 * The scale uses the Expressive spatial-fast spring so selecting a day feels physical.
 *
 * Day-state tint (D-113/D-121) is tonal and semantic, never a saturated primary: instructional
 * days use the success container, explicitly marked holiday/non-instructional days use the error
 * container, and every other day (including empty ones) stays on a neutral surface container.
 * Text uses a contrast-safe foreground, the edge is a 1px `outlineVariant` hairline at half
 * alpha, and there is no shadow. Selection stays visible as a 2dp primary ring plus the spring
 * scale so it cannot be swallowed by any tint.
 */
@Composable
private fun CalendarDayCell(
    day: M3ECalendarDay,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = M3ETheme.motion.spatialFast(),
        label = "CalendarDaySelection"
    )
    val tone = day.dayTone()
    val isInstructional = tone == M3ECalendarDayTone.INSTRUCTIONAL
    // D-121: "no teaching marked" is not a status. A day with no events (or only
    // exams/other events) stays on a neutral surface; only a real holiday /
    // non-instructional marker from the scraped data earns the error tint.
    // A published non-instructional / holiday day is a real marker even when it carries no event
    // text (the projection can classify a day purely from its VTOP day type).
    val hasExplicitNonInstructional = day.events.any(::isNonInstructionalEvent) ||
        day.dayType == M3ECalendarDayType.NON_INSTRUCTIONAL ||
        day.dayType == M3ECalendarDayType.HOLIDAY
    val containerColor = when {
        isInstructional -> colors.successContainer
        hasExplicitNonInstructional -> colors.errorContainer
        else -> colors.surfaceContainerHigh
    }
    // D-114: containers always pair with their measured on*Container token
    // (onSuccessContainer is 9.65:1 on Classic Light successContainer).
    val onContainerColor = when {
        isInstructional -> colors.onSuccessContainer
        hasExplicitNonInstructional -> colors.onErrorContainer
        else -> colors.onSurface
    }

    // One dot per distinct source, in the projection's priority order. A published day type is
    // the cell's own tint, not news, so it is deliberately not drawn as a dot.
    val dotColors = day.events.asSequence()
        .map { it.effectiveSource() }
        .filter { it != M3ECalendarSource.DAY_TYPE && it != M3ECalendarSource.OTHER }
        .distinct()
        .take(3)
        .map { sourceDotColor(it, colors) }
        .toList()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .scale(scale)
            .height(42.dp)
            .padding(2.dp)
            .clip(shape)
            .background(containerColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = when {
                    isSelected -> colors.primary
                    day.isToday -> colors.primary.copy(alpha = 0.7f)
                    else -> colors.outlineVariant.copy(alpha = 0.5f)
                },
                shape = shape
            )
            .testTag(M3ECalendarTestTags.CELL)
            .clickableA11y(
                onClickLabel = "Day ${day.date}, " +
                    if (isInstructional) "instructional" else "non-instructional",
                mergeDescendants = false,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${day.date}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = onContainerColor
            )
            if (dotColors.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    dotColors.forEach { dot ->
                        Box(Modifier.size(4.dp).clip(CircleShape).background(dot))
                    }
                }
            }
        }
    }
}

// ==========================================
// PRIVATE HELPERS
// ==========================================

/** Semantic day-state used for the calendar tint (D-113). */
internal enum class M3ECalendarDayTone { INSTRUCTIONAL, NON_INSTRUCTIONAL }

/**
 * Builds an exact 7-column grid: leading blanks + real days + trailing blanks. Trailing spacers
 * guarantee the final, partial week has seven cells too, so no pill is stretched to fill the row.
 */
internal fun buildCalendarCells(
    leadingBlanks: Int,
    days: List<M3ECalendarDay>
): List<M3ECalendarDay?> {
    val leading: List<M3ECalendarDay?> = List(leadingBlanks.coerceAtLeast(0)) { null }
    val cells: List<M3ECalendarDay?> = leading + days
    val trailing = (7 - cells.size % 7) % 7
    return cells + List(trailing) { null }
}

/** Mirrors the "instructional" detection used by the Classes event filter. */
private fun isInstructionalEvent(event: M3ECalendarEvent): Boolean =
    event.type.contains("instructional", ignoreCase = true) ||
        event.category.contains("class", ignoreCase = true)

/** Explicit non-instructional / holiday marking on the real scraped text, type or category. */
private fun isNonInstructionalEvent(event: M3ECalendarEvent): Boolean {
    val fields = listOf(event.title, event.type, event.category)
    return fields.any { field ->
        field.contains("non-instructional", ignoreCase = true) ||
            field.contains("non instructional", ignoreCase = true) ||
            field.contains("noninstructional", ignoreCase = true) ||
            field.contains("holiday", ignoreCase = true)
    }
}

/**
 * The source of an entry, with a text fallback for raw/legacy entries that predate the enriched
 * projection. The fallback is deliberately narrow: "non instructional" is checked before the
 * bare "instructional" it contains, the same ordering trap the classifier itself guards against.
 */
internal fun M3ECalendarEvent.effectiveSource(): M3ECalendarSource {
    source?.let { return it }
    val text = "$title $type $category"
    return when {
        text.contains("non-instructional", true) ||
            text.contains("non instructional", true) ||
            text.contains("noninstructional", true) -> M3ECalendarSource.DAY_TYPE
        // A holiday is its own source. Folding it into the day-type branch made a raw "Holiday"
        // entry lose its dot and its filter chip, even though the web super calendar treats a
        // holiday as a merged source in its own right.
        text.contains("holiday", true) -> M3ECalendarSource.HOLIDAY
        text.contains("exam", true) || text.contains("CAT-", true) -> M3ECalendarSource.EXAM
        text.contains("milestone", true) || text.contains("LID", true) -> M3ECalendarSource.MILESTONE
        text.contains("moodle", true) || text.contains("assignment", true) ||
            text.contains("deadline", true) || text.contains("task", true) -> M3ECalendarSource.DEADLINE
        text.contains("class", true) || text.contains("instructional", true) -> M3ECalendarSource.CLASS
        else -> M3ECalendarSource.OTHER
    }
}

/** Short, lowercase-free label for a source, safe to use as a chip or a row's second line. */
internal fun sourceLabel(source: M3ECalendarSource): String = when (source) {
    M3ECalendarSource.CLASS -> "Class"
    M3ECalendarSource.EXAM -> "Exam"
    M3ECalendarSource.MILESTONE -> "Milestone"
    M3ECalendarSource.DEADLINE -> "Deadline"
    M3ECalendarSource.HOLIDAY -> "Holiday"
    M3ECalendarSource.OD -> "OD"
    M3ECalendarSource.EVENT -> "Event"
    M3ECalendarSource.DAY_TYPE -> "Day type"
    M3ECalendarSource.OTHER -> "Other"
}

/**
 * Semantic dot colour for a source. Tonal-first: these are the same status / role tokens the rest
 * of the ledger uses, never a bespoke per-source hue. Class is the success tone, exams and holidays
 * the alert tone, deadlines the primary role, OD the secondary role, and milestones / user events
 * the restrained tertiary role.
 */
internal fun sourceDotColor(source: M3ECalendarSource, colors: M3EColorPalette): Color = when (source) {
    M3ECalendarSource.CLASS -> colors.success
    M3ECalendarSource.EXAM, M3ECalendarSource.HOLIDAY -> colors.error
    M3ECalendarSource.DEADLINE -> colors.primary
    M3ECalendarSource.OD -> colors.secondary
    M3ECalendarSource.MILESTONE, M3ECalendarSource.EVENT -> colors.tertiary
    M3ECalendarSource.DAY_TYPE, M3ECalendarSource.OTHER -> colors.outlineVariant
}

/** Semantic classification used for the day's accessibility label; the visual tint is decided separately. */
internal fun M3ECalendarDay.dayTone(): M3ECalendarDayTone {
    // The enriched projection supplies the college's own day type. Prefer it: a day carrying an
    // exam and a deadline is still an instructional day if the calendar says so. The text fallback
    // below exists only for raw entries built before the projection was wired in (and for tests).
    dayType?.let { type ->
        return when (type) {
            M3ECalendarDayType.INSTRUCTIONAL, M3ECalendarDayType.SEMIHOLIDAY ->
                M3ECalendarDayTone.INSTRUCTIONAL
            M3ECalendarDayType.NON_INSTRUCTIONAL, M3ECalendarDayType.HOLIDAY,
            M3ECalendarDayType.OTHER -> M3ECalendarDayTone.NON_INSTRUCTIONAL
        }
    }
    if (events.isEmpty()) return M3ECalendarDayTone.NON_INSTRUCTIONAL
    val hasInstructional = events.any(::isInstructionalEvent)
    val hasNonInstructional = events.any(::isNonInstructionalEvent)
    return if (hasInstructional && !hasNonInstructional) M3ECalendarDayTone.INSTRUCTIONAL
    else M3ECalendarDayTone.NON_INSTRUCTIONAL
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
