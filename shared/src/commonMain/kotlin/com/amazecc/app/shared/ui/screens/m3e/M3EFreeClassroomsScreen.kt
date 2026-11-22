package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.FreeClassroomBlockSummary
import com.amazecc.app.shared.domain.FreeClassroomDay
import com.amazecc.app.shared.domain.FreeClassroomRoomRow
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EClassroomSlot
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomKindFilter
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3EDismissibleSheet
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Rooms shown per block before the rest are hidden behind a "+N more" count (web ROW_CAP). */
private const val FREE_CLASSROOM_ROW_CAP = 12

/** Shared LIFO id: the host must push/pop this or every dismissal route no-ops. */
internal const val FREE_CLASSROOM_INSPECTOR_OVERLAY_ID = "free-classroom-inspector"

/**
 * Material 3 width classes, matching the scaffold's thresholds without adding a
 * `material3-window-size-class` dependency to commonMain.
 */
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
 * Material 3 Expressive Campus Free Classrooms Finder.
 * 100% Stateless: accepts [M3EFreeClassroomsState] and emits [M3EFreeClassroomsAction].
 * Compact windows keep the single list; medium/expanded windows split controls from
 * results so a wide screen is not one full-width column.
 *
 * D-162 adds the web page's two questions as two real sections: "When" (day + period + jump
 * to now) and "Rooms" (All/Rooms/Labs, search, block chips, block tiles). The block — not the
 * room — is the unit of the answer; no capacity is ever shown because the source has none.
 */
@Composable
fun M3EFreeClassroomsScreen(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val widthClass = rememberFreeClassroomsWidthClass()
    when (widthClass) {
        FreeClassroomsWidthClass.COMPACT -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(colors.surface),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 20.dp,
                    bottom = BOTTOM_NAV_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header Bar
                item(key = "header") {
                    FreeClassroomsHeader(onAction)
                }

                // 2. "When" — day picker + period picker + jump to now
                item(key = "when") {
                    FreeClassroomsWhenSection(state, onAction)
                }

                // 3. "Rooms" — search, kind filter, block chips and block tiles
                item(key = "rooms_header") {
                    FreeSectionHeader("Rooms", "${state.blockSummaries.sumOf { it.free }} free")
                }

                item(key = "kind_filter") {
                    M3EConnectedButtonGroup(
                        items = M3EFreeClassroomKindFilter.entries.toList(),
                        selectedItem = state.kindFilter,
                        onItemSelected = { onAction(M3EFreeClassroomsAction.SetKindFilter(it)) },
                        labelProvider = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "search") {
                    FreeClassroomsSearchField(state, onAction)
                }

                item(key = "building_filter") {
                    M3EConnectedButtonGroup(
                        items = M3EClassroomBuilding.entries,
                        selectedItem = state.selectedBuilding,
                        onItemSelected = { onAction(M3EFreeClassroomsAction.SelectBuilding(it)) },
                        labelProvider = { it.code },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "availability") {
                    FreeClassroomsAvailabilityRow(state, onAction)
                }

                item(key = "block_chips") {
                    FreeClassroomsBlockChips(state, onAction)
                }

                // 4. Loading / error feedback (driven by the host's AppState flows)
                if (state.isLoading) {
                    item(key = "progress") {
                        M3EWavyLinearProgress(
                            progress = com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE,
                            label = "Classroom availability loading"
                        )
                    }
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    item(key = "error") {
                        FreeClassroomsErrorCard(state.errorMessage)
                    }
                }

                // 5. Real block tiles (the primary answer)
                item(key = "block_tiles") {
                    FreeClassroomsBlockTiles(state, onAction, columns = 1)
                }

                // 6. Legacy per-room cards — preserved for continuity and the existing tests.
                if (state.filteredClassrooms.isEmpty()) {
                    item(key = "empty") {
                        FreeClassroomsEmptyCard(state)
                    }
                } else {
                    items(state.filteredClassrooms, key = { it.roomNumber }) { room ->
                        ClassroomSlotCard(room = room)
                    }
                }
            }
        }

        else -> {
            val expanded = widthClass == FreeClassroomsWidthClass.EXPANDED
            val columnCount = if (expanded) 2 else 1
            Row(modifier = modifier.fillMaxSize().background(colors.surface)) {
                // Controls pane
                LazyColumn(
                    modifier = Modifier
                        .weight(0.34f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = BOTTOM_NAV_PADDING
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "header") { FreeClassroomsHeader(onAction) }
                    item(key = "when") { FreeClassroomsWhenSection(state, onAction) }
                    item(key = "rooms_header") {
                        FreeSectionHeader("Rooms", "${state.blockSummaries.sumOf { it.free }} free")
                    }
                    item(key = "kind_filter") {
                        M3EConnectedButtonGroup(
                            items = M3EFreeClassroomKindFilter.entries.toList(),
                            selectedItem = state.kindFilter,
                            onItemSelected = { onAction(M3EFreeClassroomsAction.SetKindFilter(it)) },
                            labelProvider = { it.label },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "search") { FreeClassroomsSearchField(state, onAction) }
                    item(key = "building_filter") {
                        M3EConnectedButtonGroup(
                            items = M3EClassroomBuilding.entries,
                            selectedItem = state.selectedBuilding,
                            onItemSelected = { onAction(M3EFreeClassroomsAction.SelectBuilding(it)) },
                            labelProvider = { it.code },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "availability") { FreeClassroomsAvailabilityRow(state, onAction) }
                    item(key = "block_chips") { FreeClassroomsBlockChips(state, onAction) }
                }

                // Results pane
                LazyColumn(
                    modifier = Modifier
                        .weight(0.66f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = BOTTOM_NAV_PADDING
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isLoading) {
                        item(key = "progress") {
                            M3EWavyLinearProgress(
                                progress = com.amazecc.app.shared.ui.m3e.components.M3E_WAVY_INDETERMINATE,
                                label = "Classroom availability loading"
                            )
                        }
                    }
                    if (!state.errorMessage.isNullOrBlank()) {
                        item(key = "error") { FreeClassroomsErrorCard(state.errorMessage) }
                    }
                    item(key = "block_tiles") {
                        FreeClassroomsBlockTiles(state, onAction, columns = columnCount)
                    }
                    if (state.filteredClassrooms.isEmpty()) {
                        item(key = "empty") { FreeClassroomsEmptyCard(state) }
                    } else {
                        items(state.filteredClassrooms.chunked(columnCount)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { room ->
                                    ClassroomSlotCard(room = room, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    FreeClassroomsInspectorSheet(state, onAction)
}

// ── D-162: shared section primitives ──────────────────────────────────────────

@Composable
private fun FreeClassroomsHeader(onAction: (M3EFreeClassroomsAction) -> Unit) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Free Classrooms",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = colors.onSurface
            )
            Text(
                text = "Find quiet study spots & empty halls",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { onAction(M3EFreeClassroomsAction.Refresh) },
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun FreeSectionHeader(title: String, trailing: String? = null) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary
            )
        }
    }
}

@Composable
private fun FreeClassroomsWhenSection(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 24.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FreeSectionHeader("When", state.nowLabel.takeIf { it.isNotBlank() })
            M3EConnectedButtonGroup(
                items = FreeClassroomDay.entries.toList(),
                selectedItem = FreeClassroomDay.fromKey(state.selectedDay),
                onItemSelected = { onAction(M3EFreeClassroomsAction.SelectDay(it.key)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onAction(M3EFreeClassroomsAction.StepPeriod(-1)) },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous period",
                        tint = colors.primary
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val period = state.availablePeriodOptions.firstOrNull { it.key == state.selectedPeriodKey }
                    val index = state.availablePeriodOptions.indexOfFirst { it.key == state.selectedPeriodKey }
                    Text(
                        text = period?.label ?: state.selectedPeriodKey,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    if (index >= 0) {
                        Text(
                            text = "Period ${index + 1} of ${state.availablePeriodOptions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { onAction(M3EFreeClassroomsAction.StepPeriod(1)) },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next period",
                        tint = colors.primary
                    )
                }
                if (state.nowPeriodKey.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onAction(M3EFreeClassroomsAction.SelectDay(state.nowDayKey))
                            onAction(M3EFreeClassroomsAction.SelectPeriod(state.nowPeriodKey))
                        },
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Jump to now",
                            tint = colors.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeClassroomsSearchField(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    val colors = M3ETheme.colors
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = { onAction(M3EFreeClassroomsAction.SearchQueryChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Search room (e.g. SJT-401, TT-712)...",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.primary
            )
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerLow,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outlineVariant,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant
        )
    )
}

@Composable
private fun FreeClassroomsAvailabilityRow(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = state.onlyAvailableNow,
            onClick = { onAction(M3EFreeClassroomsAction.ToggleOnlyAvailable(!state.onlyAvailableNow)) },
            label = {
                Text(
                    text = "Only Free Right Now",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            leadingIcon = {
                if (state.onlyAvailableNow) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer,
                selectedLeadingIconColor = colors.onPrimaryContainer
            )
        )
        Text(
            text = "${state.filteredClassrooms.size} rooms found",
            style = MaterialTheme.typography.bodySmall,
            color = colors.primary
        )
    }
}

@Composable
private fun FreeClassroomsBlockChips(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    if (state.blockSummaries.size < 2) return
    val options = listOf("All") + state.blockSummaries.map { it.block }
    M3EConnectedButtonGroup(
        items = options,
        selectedItem = if (state.blockFilter in options) state.blockFilter else "All",
        onItemSelected = { onAction(M3EFreeClassroomsAction.SetBlockFilter(it)) },
        labelProvider = { block ->
            if (block == "All") "All blocks" else {
                val free = state.blockSummaries.firstOrNull { it.block == block }?.free ?: 0
                "$block · $free"
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FreeClassroomsBlockTiles(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit,
    columns: Int
) {
    val query = state.searchQuery.trim()
    val matches: (FreeClassroomRoomRow) -> Boolean = { room ->
        query.isBlank() || room.code.contains(query, ignoreCase = true) ||
            room.number.contains(query, ignoreCase = true)
    }
    val visible = state.blockSummaries
        .filter { state.blockFilter == "All" || it.block == state.blockFilter }
        .map { summary ->
            val rooms = when (state.kindFilter) {
                M3EFreeClassroomKindFilter.ALL -> summary.freeRooms.filter(matches)
                M3EFreeClassroomKindFilter.THEORY -> summary.freeRooms.filter { !it.isLab && matches(it) }
                M3EFreeClassroomKindFilter.LAB -> summary.freeRooms.filter { it.isLab && matches(it) }
            }
            summary to rooms
        }
        .filter { (_, rooms) -> rooms.isNotEmpty() }

    if (visible.isEmpty()) {
        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.LOW,
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.MeetingRoom,
                    contentDescription = null,
                    tint = M3ETheme.colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No rooms free in this selection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = M3ETheme.colors.onSurfaceVariant
                )
            }
        }
        return
    }

    visible.chunked(columns.coerceAtLeast(1)).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            row.forEach { (summary, rooms) ->
                FreeClassroomBlockTile(
                    summary = summary,
                    rooms = rooms,
                    onAction = onAction,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FreeClassroomBlockTile(
    summary: FreeClassroomBlockSummary,
    rooms: List<FreeClassroomRoomRow>,
    onAction: (M3EFreeClassroomsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    // The visible free count is the one behind the active search / kind filter, exactly as the
    // web page recomputes it; the denominator stays the block's real room total.
    val visibleFree = rooms.size
    val percent = if (summary.total > 0) ((visibleFree * 100f) / summary.total).toInt() else 0
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 22.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${summary.block} Block",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$percent% free",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
            }
            Text(
                text = "$visibleFree of ${summary.total} free",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = colors.primary,
                trackColor = colors.surfaceContainerHighest
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rooms.take(FREE_CLASSROOM_ROW_CAP).forEach { room ->
                    FreeClassroomRoomChip(room = room, onAction = onAction)
                }
                val hidden = rooms.size - minOf(rooms.size, FREE_CLASSROOM_ROW_CAP)
                if (hidden > 0) {
                    Text(
                        text = "+$hidden more in ${summary.block}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeClassroomRoomChip(
    room: FreeClassroomRoomRow,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    val colors = M3ETheme.colors
    val tint = if (room.isLab) colors.tertiary else colors.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable { onAction(M3EFreeClassroomsAction.SelectRoom(room.code)) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = room.number,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = tint
        )
        if (room.isLab) {
            Icon(
                imageVector = Icons.Rounded.Science,
                contentDescription = "Lab",
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun FreeClassroomsInspectorSheet(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit
) {
    val room = state.selectedRoom?.let { code ->
        state.allDerivedRooms.firstOrNull { it.code == code }
    }
    M3EDismissibleSheet(
        isOpen = room != null,
        onDismiss = { onAction(M3EFreeClassroomsAction.ToggleRoomInspector) },
        title = room?.code ?: "",
        overlayId = FREE_CLASSROOM_INSPECTOR_OVERLAY_ID
    ) {
        if (room != null) {
            val colors = M3ETheme.colors
            val period = state.availablePeriodOptions.firstOrNull { it.key == state.selectedPeriodKey }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${room.block} Block · ${if (room.isLab) "Lab" else "Classroom"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                IconButton(onClick = { onAction(M3EFreeClassroomsAction.ToggleRoomInspector) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close room inspector", tint = colors.onSurface)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (room.isFree) {
                    "Free during ${period?.label ?: state.selectedPeriodKey}"
                } else {
                    "Occupied by ${room.courseCode} (${room.slot}) during ${period?.label ?: state.selectedPeriodKey}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (room.isFree) colors.primary else colors.onSurfaceVariant
            )
            if (room.courseTitle.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = room.courseTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FreeClassroomsErrorCard(message: String) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
    }
}

@Composable
private fun FreeClassroomsEmptyCard(state: M3EFreeClassroomsState) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.MeetingRoom,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (state.classrooms.isEmpty()) {
                    "No classrooms available right now"
                } else {
                    "No classrooms match the selected filters"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClassroomSlotCard(
    room: M3EClassroomSlot,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = if (room.isCurrentlyAvailable) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
        cornerRadius = 22.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Room Number & Availability Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (room.isCurrentlyAvailable) colors.primaryContainer else colors.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = room.roomNumber,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (room.isCurrentlyAvailable) colors.onPrimaryContainer else colors.onSurface
                        )
                    }

                    if (room.isAirConditioned) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.tertiaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AcUnit,
                                    contentDescription = null,
                                    tint = colors.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "AC",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (room.isCurrentlyAvailable) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (room.isCurrentlyAvailable) "FREE NOW" else "OCCUPIED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = if (room.isCurrentlyAvailable) colors.primary else colors.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Building Name & Floor
            Text(
                text = "${room.buildingCode} Block • Floor ${room.floor}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status message & Capacity (legacy cache field, preserved for continuity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = if (room.isCurrentlyAvailable) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = room.availabilityStatus,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (room.isCurrentlyAvailable) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (room.isCurrentlyAvailable) colors.primary else colors.onSurfaceVariant
                    )
                }

                // No capacity row: the source never publishes seats per room, so showing
                // a number here would fabricate a fact (the legacy model field defaults to 0).
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3EFreeClassroomsScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EFreeClassroomsScreen(
            state = createMockFreeClassroomsState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3EFreeClassroomsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EFreeClassroomsScreen(
            state = createMockFreeClassroomsState(),
            onAction = {}
        )
    }
}

private fun createMockFreeClassroomsState(): M3EFreeClassroomsState {
    val sampleRooms = listOf(
        M3EClassroomSlot(
            roomNumber = "SJT-401",
            buildingCode = "SJT",
            floor = 4,
            capacity = 60,
            isAirConditioned = true,
            availabilityStatus = "Free until 02:00 PM (B2+TB2 slot)",
            isCurrentlyAvailable = true,
            nextOccupiedTime = "02:00 PM"
        ),
        M3EClassroomSlot(
            roomNumber = "SJT-402",
            buildingCode = "SJT",
            floor = 4,
            capacity = 65,
            isAirConditioned = true,
            availabilityStatus = "Occupied until 11:40 AM (A1 slot)",
            isCurrentlyAvailable = false,
            nextOccupiedTime = "11:40 AM"
        ),
        M3EClassroomSlot(
            roomNumber = "TT-712",
            buildingCode = "TT",
            floor = 7,
            capacity = 70,
            isAirConditioned = false,
            availabilityStatus = "Free all afternoon",
            isCurrentlyAvailable = true,
            nextOccupiedTime = null
        ),
        M3EClassroomSlot(
            roomNumber = "MB-108",
            buildingCode = "MB",
            floor = 1,
            capacity = 80,
            isAirConditioned = false,
            availabilityStatus = "Free until 04:00 PM",
            isCurrentlyAvailable = true,
            nextOccupiedTime = "04:00 PM"
        )
    )

    return M3EFreeClassroomsState(
        classrooms = sampleRooms,
        filteredClassrooms = sampleRooms,
        selectedBuilding = M3EClassroomBuilding.ALL,
        onlyAvailableNow = true,
        searchQuery = ""
    )
}
