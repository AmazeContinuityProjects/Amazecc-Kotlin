package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.FREE_CLASSROOM_RUN_LENGTH
import com.amazecc.app.shared.domain.FreeClassroomBlockSummary
import com.amazecc.app.shared.domain.FreeClassroomDay
import com.amazecc.app.shared.domain.FreeClassroomRoomRow
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomClassDetails
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomKindFilter
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomTimelineEntry
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.components.clickableA11y
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Web `ROW_CAP`: room chips shown per block before "+N more". */
private const val FREE_CLASSROOM_ROW_CAP = 12

/** Unique LIFO ids. The host must push/pop these or every sheet dismissal route no-ops. */
internal const val FREE_CLASSROOM_INSPECTOR_OVERLAY_ID = "free-classroom-inspector"
internal const val FREE_CLASSROOM_CLASS_DETAILS_OVERLAY_ID = "free-classroom-class-details"
internal const val FREE_CLASSROOM_FILTERS_OVERLAY_ID = "free-classroom-filters"

private val TEACHING_DAYS: List<FreeClassroomDay> = FreeClassroomDay.entries.filter { it != FreeClassroomDay.SAT }

private enum class FreeClassroomsWidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
private fun rememberFreeClassroomsWidthClass(): FreeClassroomsWidthClass {
    val widthDp = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density
    return when {
        widthDp < 600f -> FreeClassroomsWidthClass.COMPACT
        widthDp < 840f -> FreeClassroomsWidthClass.MEDIUM
        else -> FreeClassroomsWidthClass.EXPANDED
    }
}

/**
 * D-191 — Material 3 Expressive free-classroom finder.
 *
 * 100% stateless: accepts [M3EFreeClassroomsState] and emits [M3EFreeClassroomsAction]. Every
 * filter has already been applied by the host, so [M3EFreeClassroomsState.blockSummaries] is the
 * ONE answer and [M3EFreeClassroomsState.resultCount] is the ONLY result count. There is no legacy
 * per-room card list, no second counter and no fabricated capacity/AC/status text.
 */
@Composable
fun M3EFreeClassroomsScreen(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        !state.errorMessage.isNullOrBlank() && !state.isLoading -> FreeClassroomsErrorPage(state.errorMessage, onAction, modifier)
        state.isLoading -> FreeClassroomsLoadingPage(modifier)
        else -> FreeClassroomsContent(state, onAction, modifier)
    }
}

// ── honest full-page states ───────────────────────────────────────────────────

@Composable
private fun FreeClassroomsErrorPage(
    message: String,
    onAction: (M3EFreeClassroomsAction) -> Unit,
    modifier: Modifier
) {
    val colors = M3ETheme.colors
    Box(modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.Center) {
        M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 28.dp, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = colors.error, modifier = Modifier.size(40.dp))
                Text("Couldn't load the timetable", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(message, fontSize = 13.sp, color = colors.onSurfaceVariant)
                Button(onClick = { onAction(M3EFreeClassroomsAction.Refresh) }) {
                    Text("Try again", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun FreeClassroomsLoadingPage(modifier: Modifier) {
    val colors = M3ETheme.colors
    Column(
        modifier = modifier.fillMaxSize().background(colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Free Classrooms", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        M3EWavyLinearProgress(
            progress = M3E_WAVY_INDETERMINATE,
            label = "Classroom availability loading"
        )
        repeat(4) {
            M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(96.dp).background(colors.surfaceContainerHigh))
            }
        }
    }
}

// ── the one answer surface ────────────────────────────────────────────────────

@Composable
private fun FreeClassroomsContent(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit,
    modifier: Modifier
) {
    val colors = M3ETheme.colors
    val widthClass = rememberFreeClassroomsWidthClass()
    when (widthClass) {
        FreeClassroomsWidthClass.COMPACT -> {
            LazyColumn(
                modifier = modifier.fillMaxSize().background(colors.surface),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = BOTTOM_NAV_PADDING),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header") { FreeClassroomsHeader(onAction) }
                item(key = "when") { FreeClassroomsWhenCard(state, onAction) }
                item(key = "insights") { FreeClassroomsInsights(state, onAction) }
                item(key = "search") { FreeClassroomsSearch(state, onAction) }
                item(key = "results_header") { FreeClassroomsResultsHeader(state, onAction) }
                if (state.blockSummaries.isEmpty()) {
                    item(key = "empty") { FreeClassroomsEmpty(state, onAction) }
                } else {
                    items(state.blockSummaries, key = { it.block }) { block ->
                        FreeClassroomBlockTile(block, state, onAction)
                    }
                }
                item(key = "footer") {
                    Text(
                        text = "Rooms based on the official campus FFCS course catalog and your signed-in timetable.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        else -> {
            val resultsWeight = if (widthClass == FreeClassroomsWidthClass.EXPANDED) 0.66f else 0.6f
            Row(modifier = modifier.fillMaxSize().background(colors.surface)) {
                LazyColumn(
                    modifier = Modifier.weight(1f - resultsWeight).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 20.dp, bottom = BOTTOM_NAV_PADDING),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "header") { FreeClassroomsHeader(onAction) }
                    item(key = "when") { FreeClassroomsWhenCard(state, onAction) }
                    item(key = "insights") { FreeClassroomsInsights(state, onAction) }
                    item(key = "search") { FreeClassroomsSearch(state, onAction) }
                    item(key = "results_header") { FreeClassroomsResultsHeader(state, onAction) }
                }
                LazyColumn(
                    modifier = Modifier.weight(resultsWeight).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 8.dp, end = 20.dp, top = 20.dp, bottom = BOTTOM_NAV_PADDING),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.blockSummaries.isEmpty()) {
                        item(key = "empty") { FreeClassroomsEmpty(state, onAction) }
                    } else {
                        items(state.blockSummaries, key = { it.block }) { block ->
                            FreeClassroomBlockTile(block, state, onAction)
                        }
                    }
                }
            }
        }
    }
    FreeClassroomsOverlays(state, onAction)
}

@Composable
private fun FreeClassroomsHeader(onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Free Classrooms", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text("Find an empty room on campus", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        IconButton(
            onClick = { onAction(M3EFreeClassroomsAction.Refresh) },
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = colors.primary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FreeClassroomsWhenCard(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    val periods = state.availablePeriodOptions.filter { !it.isLunch }
    val index = periods.indexOfFirst { it.key == state.selectedPeriodKey }
    val hasNow = state.nowPeriodKey.isNotBlank() && state.nowDayKey.isNotBlank()

    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("When", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.clockLabel.isNotBlank()) {
                        Text(state.clockLabel, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    Text(
                        text = if (state.isLive) "Live" else "Selected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isLive) colors.success else colors.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    // "Now" lives here, not in the period row: that row keeps at most two labelled
                    // controls (previous / next period).
                    if (hasNow) {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.primaryContainer)
                                .clickableA11y(onClickLabel = "Jump to now") { onAction(M3EFreeClassroomsAction.JumpToNow) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                        }
                    }
                }
            }
            M3EConnectedButtonGroup(
                items = TEACHING_DAYS,
                selectedItem = FreeClassroomDay.fromKey(state.selectedDay),
                onItemSelected = { onAction(M3EFreeClassroomsAction.SelectDay(it.key)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onAction(M3EFreeClassroomsAction.StepPeriod(-1)) },
                    enabled = index > 0,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous period", tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = periods.getOrNull(index)?.label ?: state.selectedPeriodKey,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text("Period ${if (index >= 0) index + 1 else 0} of ${periods.size}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                IconButton(
                    onClick = { onAction(M3EFreeClassroomsAction.StepPeriod(1)) },
                    enabled = index >= 0 && index < periods.lastIndex,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next period", tint = colors.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FreeClassroomsInsights(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // No strict live period means no "Free now" number exists. Hide the card instead of
        // labelling a stale selected-period count as live availability.
        if (state.insights.isLiveNow) {
            FreeInsightCard(
                label = "Free now",
                value = state.insights.freeNowCount,
                sub = "Jump to the live period",
                onClickLabel = "Show free now"
            ) { onAction(M3EFreeClassroomsAction.ApplyInsight("free_now")) }
        }
        FreeInsightCard(
            label = "Emptiest period",
            value = state.insights.bestPeriodFree,
            sub = state.insights.bestPeriodLabel ?: "No free period",
            enabled = state.insights.bestPeriodKey != null,
            onClickLabel = "Show the emptiest period"
        ) { onAction(M3EFreeClassroomsAction.ApplyInsight("emptiest")) }
        FreeInsightCard(
            label = "Free for $FREE_CLASSROOM_RUN_LENGTH periods",
            value = state.insights.runFreeCount,
            sub = "From this period onward",
            onClickLabel = "Show rooms free for the next three periods"
        ) { onAction(M3EFreeClassroomsAction.ApplyInsight("run")) }
    }
}

@Composable
private fun FreeInsightCard(
    label: String,
    value: Int,
    sub: String,
    enabled: Boolean = true,
    onClickLabel: String,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors
    val tint = if (enabled) colors.primary else colors.onSurfaceVariant
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .clickableA11y(onClickLabel = onClickLabel, enabled = enabled) { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
        Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(sub, fontSize = 12.sp, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun FreeClassroomsSearch(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = { onAction(M3EFreeClassroomsAction.SearchQueryChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search room (e.g. 401, AB3)…", fontSize = 12.sp, color = colors.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.primary) },
        trailingIcon = {
            if (state.searchQuery.isNotEmpty()) {
                IconButton(onClick = { onAction(M3EFreeClassroomsAction.SearchQueryChanged("")) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = colors.onSurfaceVariant)
                }
            }
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
}

@Composable
private fun FreeClassroomsResultsHeader(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // FC-local feedback stays beside the valid answer; it never replaces the page.
        if (!state.inlineNotice.isNullOrBlank()) {
            Text(state.inlineNotice, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Rooms", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text("${state.resultCount} free", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHigh)
                    .clickableA11y(onClickLabel = "Open filters") { onAction(M3EFreeClassroomsAction.SetFiltersOpen(true)) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.FilterList, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Text("Filters", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                }
            }
            FilterToggle(
                label = "Only free",
                selected = state.onlyAvailableNow,
                onClick = { onAction(M3EFreeClassroomsAction.ToggleOnlyAvailable(!state.onlyAvailableNow)) },
                modifier = Modifier.height(48.dp).weight(1f)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FreeClassroomBlockTile(
    summary: FreeClassroomBlockSummary,
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    val colors = M3ETheme.colors
    val rooms = if (state.onlyAvailableNow) summary.freeRooms else summary.rooms
    val expanded = summary.block in state.expandedBlocks
    val shown = if (expanded) rooms else rooms.take(FREE_CLASSROOM_ROW_CAP)
    val hidden = rooms.size - shown.size
    val pct = if (summary.total > 0) ((summary.freeRooms.size * 100f) / summary.total).toInt() else 0

    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.HIGH, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${summary.block} Block", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(
                    "$pct% free",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text("${summary.freeRooms.size} of ${summary.total} free", fontSize = 12.sp, color = colors.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { (summary.freeRooms.size.toFloat() / summary.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = colors.primary,
                trackColor = colors.surfaceContainerHighest
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { room ->
                    FreeClassroomRoomChip(
                        room = room,
                        copied = state.copiedRoom == room.code,
                        onAction = onAction
                    )
                }
            }
            if (hidden > 0) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickableA11y(onClickLabel = "Show $hidden more rooms in ${summary.block}") {
                            onAction(M3EFreeClassroomsAction.ToggleBlockExpanded(summary.block))
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+$hidden more in ${summary.block}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                }
            }
            if (expanded && rooms.size > FREE_CLASSROOM_ROW_CAP) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickableA11y(onClickLabel = "Show fewer rooms in ${summary.block}") {
                            onAction(M3EFreeClassroomsAction.ToggleBlockExpanded(summary.block))
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Show fewer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** One chip, two separate targets: the number inspects, the trailing icon copies. */
@Composable
private fun FreeClassroomRoomChip(
    room: FreeClassroomRoomRow,
    copied: Boolean,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    val colors = M3ETheme.colors
    val tint = if (room.isLab) colors.tertiary else colors.primary
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.10f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .clickableA11y(onClickLabel = "Inspect ${room.code}") { onAction(M3EFreeClassroomsAction.SelectRoom(room.code)) }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.number, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
                if (room.isLab) {
                    Icon(Icons.Rounded.Science, contentDescription = "Lab", tint = tint, modifier = Modifier.size(14.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .height(48.dp)
                .width(48.dp)
                .clickableA11y(onClickLabel = "Copy ${room.code}") { onAction(M3EFreeClassroomsAction.CopyRoom(room.code)) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = if (copied) "Copied ${room.code}" else "Copy ${room.code}",
                tint = if (copied) colors.success else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FreeClassroomsEmpty(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    val noDomainData = state.availableBlocks.isEmpty()
    val title = when {
        noDomainData -> "No timetable data synced yet"
        state.blockFilter != "All" -> "Nothing free in ${state.blockFilter}"
        else -> "Every room is taken for this period"
    }
    val body = when {
        noDomainData -> "Free rooms are derived from your signed-in timetable and the FFCS catalogue. Refresh to try again."
        state.blockFilter != "All" -> "No rooms are free in ${state.blockFilter} for this period. Try all blocks or another time."
        else -> "Every room on campus is booked for this period. Jump to the current slot or pick another time."
    }
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(body, fontSize = 12.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!noDomainData && state.blockFilter != "All") {
                    TextButton(onClick = { onAction(M3EFreeClassroomsAction.SetBlockFilter("All")) }) {
                        Text("All blocks", fontSize = 13.sp)
                    }
                }
                if (!noDomainData) {
                    TextButton(onClick = { onAction(M3EFreeClassroomsAction.JumpToNow) }) {
                        Text("Jump to now", fontSize = 13.sp)
                    }
                } else {
                    TextButton(onClick = { onAction(M3EFreeClassroomsAction.Refresh) }) {
                        Text("Try again", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── overlays: one Filters sheet, one room inspector, one class detail ──────────

@Composable
private fun FreeClassroomsOverlays(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    FreeClassroomsFiltersSheet(state, onAction)
    FreeClassroomsInspectorSheet(state, onAction)
    state.selectedClass?.let { FreeClassroomsClassSheet(it, onAction) }
}

@Composable
private fun FreeClassroomsFiltersSheet(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    M3EDismissibleSheet(
        isOpen = state.filtersOpen,
        onDismiss = { onAction(M3EFreeClassroomsAction.SetFiltersOpen(false)) },
        title = "Filters",
        overlayId = FREE_CLASSROOM_FILTERS_OVERLAY_ID
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetSectionTitle("Kind")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterToggle(
                    label = "Theory",
                    selected = state.kindFilter == M3EFreeClassroomKindFilter.THEORY,
                    onClick = {
                        val next = if (state.kindFilter == M3EFreeClassroomKindFilter.THEORY) {
                            M3EFreeClassroomKindFilter.ALL
                        } else {
                            M3EFreeClassroomKindFilter.THEORY
                        }
                        onAction(M3EFreeClassroomsAction.SetKindFilter(next))
                    },
                    modifier = Modifier.height(48.dp).weight(1f)
                )
                FilterToggle(
                    label = "Labs",
                    selected = state.kindFilter == M3EFreeClassroomKindFilter.LAB,
                    onClick = {
                        val next = if (state.kindFilter == M3EFreeClassroomKindFilter.LAB) {
                            M3EFreeClassroomKindFilter.ALL
                        } else {
                            M3EFreeClassroomKindFilter.LAB
                        }
                        onAction(M3EFreeClassroomsAction.SetKindFilter(next))
                    },
                    modifier = Modifier.height(48.dp).weight(1f)
                )
            }
            if (state.kindFilter == M3EFreeClassroomKindFilter.ALL) {
                Text("All rooms", fontSize = 12.sp, color = M3ETheme.colors.onSurfaceVariant)
            }

            SheetSectionTitle("Stay")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterToggle(
                    label = "Only free",
                    selected = state.onlyAvailableNow,
                    onClick = { onAction(M3EFreeClassroomsAction.ToggleOnlyAvailable(!state.onlyAvailableNow)) },
                    modifier = Modifier.height(48.dp).weight(1f)
                )
                FilterToggle(
                    label = "Free for $FREE_CLASSROOM_RUN_LENGTH periods",
                    selected = state.runOnly,
                    onClick = { onAction(M3EFreeClassroomsAction.SetRunOnly(!state.runOnly)) },
                    modifier = Modifier.height(48.dp).weight(1f)
                )
            }

            SheetSectionTitle("Building")
            M3EClassroomBuilding.entries.toList().chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { building ->
                        FilterToggle(
                            label = building.code,
                            selected = state.selectedBuilding == building,
                            onClick = { onAction(M3EFreeClassroomsAction.SelectBuilding(building)) },
                            modifier = Modifier.height(48.dp).weight(1f)
                        )
                    }
                }
            }

            SheetSectionTitle("Block")
            (listOf("All") + state.availableBlocks).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { block ->
                        FilterToggle(
                            label = if (block == "All") "All blocks" else block,
                            selected = state.blockFilter == block,
                            onClick = { onAction(M3EFreeClassroomsAction.SetBlockFilter(block)) },
                            modifier = Modifier.height(48.dp).weight(1f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAction(M3EFreeClassroomsAction.SetKindFilter(M3EFreeClassroomKindFilter.ALL))
                        onAction(M3EFreeClassroomsAction.SelectBuilding(M3EClassroomBuilding.ALL))
                        onAction(M3EFreeClassroomsAction.SetBlockFilter("All"))
                        onAction(M3EFreeClassroomsAction.ToggleOnlyAvailable(true))
                        onAction(M3EFreeClassroomsAction.SetRunOnly(false))
                        onAction(M3EFreeClassroomsAction.SearchQueryChanged(""))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.height(48.dp).weight(1f)
                ) { Text("Reset", fontSize = 13.sp) }
                Button(
                    onClick = { onAction(M3EFreeClassroomsAction.SetFiltersOpen(false)) },
                    modifier = Modifier.height(48.dp).weight(1f)
                ) { Text("Done", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun SheetSectionTitle(title: String) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = M3ETheme.colors.onSurface)
}

@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = M3ETheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .clickableA11y(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun FreeClassroomsInspectorSheet(state: M3EFreeClassroomsState, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val roomCode = state.selectedRoom ?: return
    val colors = M3ETheme.colors
    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = { onAction(M3EFreeClassroomsAction.ToggleRoomInspector) },
        title = roomCode,
        overlayId = FREE_CLASSROOM_INSPECTOR_OVERLAY_ID
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val freeCount = state.roomTimeline.count { it.isFree }
                Text(
                    "${fullDayLabel(state.inspectDay)} · $freeCount/${state.roomTimeline.size} free",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
                TextButton(onClick = { onAction(M3EFreeClassroomsAction.CopyRoom(roomCode)) }) {
                    Text(
                        text = if (state.copiedRoom == roomCode) "Copied" else "Copy room code",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // The sheet owns its day. Browsing the room's week must not move the list underneath it.
            M3EConnectedButtonGroup(
                items = TEACHING_DAYS,
                selectedItem = FreeClassroomDay.fromKey(state.inspectDay),
                onItemSelected = { onAction(M3EFreeClassroomsAction.SelectInspectDay(it.key)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.inspectDay != state.selectedDay) {
                val label = "Show ${fullDayLabel(state.inspectDay)} in the list"
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickableA11y(onClickLabel = label) {
                            onAction(M3EFreeClassroomsAction.ShowDayInList(state.inspectDay))
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.roomTimeline.forEach { entry ->
                    FreeClassroomsTimelineRow(entry, onAction)
                }
                if (state.roomTimeline.isEmpty()) {
                    Text("No timetable periods are published for this room.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FreeClassroomsTimelineRow(entry: M3EFreeClassroomTimelineEntry, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    val label = if (entry.isFree) "Show ${entry.label} in the list" else "Open class details for ${entry.label}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (entry.isSelected) colors.primaryContainer else colors.surfaceContainerLow)
            .clickableA11y(onClickLabel = label) {
                val occupant = entry.occupants.firstOrNull()
                if (occupant == null) {
                    onAction(M3EFreeClassroomsAction.SelectPeriod(entry.periodKey))
                } else {
                    onAction(M3EFreeClassroomsAction.OpenClass(occupant.key))
                }
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(entry.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(
                if (entry.isFree) "Free" else "Taken",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (entry.isFree) colors.success else colors.onSurfaceVariant
            )
        }
        Text(
            text = if (entry.isFree) {
                "Free and available"
            } else {
                // Only the classes meeting in THIS period are named, never the room's whole day.
                entry.occupants.joinToString(" + ") { occupant ->
                    listOfNotNull(
                        occupant.title.ifBlank { occupant.courseCode }.takeIf { it.isNotBlank() },
                        occupant.courseCode.takeIf { it.isNotBlank() && occupant.title.isNotBlank() },
                        occupant.faculty.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                }
            },
            fontSize = 12.sp,
            color = colors.onSurfaceVariant
        )
        if (entry.isSelected) {
            Text("Selected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        }
    }
}

@Composable
private fun FreeClassroomsClassSheet(details: M3EFreeClassroomClassDetails, onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    M3EDismissibleSheet(
        isOpen = true,
        onDismiss = { onAction(M3EFreeClassroomsAction.CloseClass) },
        title = details.title.ifBlank { details.courseCode.ifBlank { "Class details" } },
        overlayId = FREE_CLASSROOM_CLASS_DETAILS_OVERLAY_ID
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = listOfNotNull(
                    details.courseCode.takeIf { it.isNotBlank() },
                    details.type.takeIf { it.isNotBlank() },
                    details.roomCode.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
            InfoRow("Faculty", details.faculty.ifBlank { "Not published" })
            if (!details.erpId.isNullOrBlank()) InfoRow("ERP ID", details.erpId)
            InfoRow("Slot", details.slot.ifBlank { "Not published" })
            if (!details.classId.isNullOrBlank()) InfoRow("Class ID", details.classId)
            InfoRow("When", "${details.dayLabel} · ${details.periodLabel}")
            InfoRow("Source", if (details.source == "catalogue") "FFCS catalogue" else "Signed-in timetable")

            if (!details.erpId.isNullOrBlank()) {
                Button(
                    onClick = { onAction(M3EFreeClassroomsAction.ViewFacultySchedule(details.erpId, details.schoolId)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("View Faculty Schedule", fontSize = 13.sp)
                }
            } else {
                Text("Faculty schedule is unavailable: no ERP id was published for this class.", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = M3ETheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
    }
}

private fun fullDayLabel(key: String): String = when (FreeClassroomDay.fromKey(key)) {
    FreeClassroomDay.MON -> "Monday"
    FreeClassroomDay.TUE -> "Tuesday"
    FreeClassroomDay.WED -> "Wednesday"
    FreeClassroomDay.THU -> "Thursday"
    FreeClassroomDay.FRI -> "Friday"
    FreeClassroomDay.SAT -> "Saturday"
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3EFreeClassroomsScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EFreeClassroomsScreen(state = createMockFreeClassroomsState(), onAction = {})
    }
}

@Preview
@Composable
fun M3EFreeClassroomsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EFreeClassroomsScreen(state = createMockFreeClassroomsState(), onAction = {})
    }
}

private fun createMockFreeClassroomsState(): M3EFreeClassroomsState {
    val rooms = listOf(
        FreeClassroomRoomRow("SJT-401", "SJT", "401", isLab = false, isFree = true),
        FreeClassroomRoomRow("TT-201", "TT", "201", isLab = true, isFree = true)
    )
    val blocks = listOf(
        FreeClassroomBlockSummary("SJT", 4, 1, 1, 0, listOf(rooms[0]), listOf(rooms[0])),
        FreeClassroomBlockSummary("TT", 3, 1, 0, 1, listOf(rooms[1]), listOf(rooms[1]))
    )
    return M3EFreeClassroomsState(
        selectedDay = "MON",
        selectedPeriodKey = "p1",
        availablePeriodOptions = listOf(
            com.amazecc.app.shared.domain.FreeClassroomPeriodOption("p1", "08:00 - 08:50", 480, 530),
            com.amazecc.app.shared.domain.FreeClassroomPeriodOption("p2", "09:00 - 09:50", 540, 590)
        ),
        blockSummaries = blocks,
        availableBlocks = listOf("SJT", "TT"),
        clockLabel = "08:02 · Mon",
        insights = com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomInsights(
            isLiveNow = true,
            freeNowCount = 2,
            bestPeriodKey = "p2",
            bestPeriodLabel = "09:00 - 09:50",
            bestPeriodFree = 3,
            runFreeCount = 1
        )
    )
}
