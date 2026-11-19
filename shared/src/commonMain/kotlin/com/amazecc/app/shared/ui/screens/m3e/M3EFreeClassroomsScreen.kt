package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EClassroomSlot
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Campus Free Classrooms Finder.
 * 100% Stateless: accepts [M3EFreeClassroomsState] and emits [M3EFreeClassroomsAction].
 * Features building filters (SJT, TT, MB, etc.), instant study spot search, and live occupancy status.
 */
@Composable
fun M3EFreeClassroomsScreen(
    state: M3EFreeClassroomsState,
    onAction: (M3EFreeClassroomsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar
        item {
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
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Search Field
        item {
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

        // 3. Building Filter Segment
        item {
            M3EConnectedButtonGroup(
                items = M3EClassroomBuilding.entries,
                selectedItem = state.selectedBuilding,
                onItemSelected = { onAction(M3EFreeClassroomsAction.SelectBuilding(it)) },
                labelProvider = { it.code },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. Quick Availability Filter Chip
        item {
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

        // 5. Loading / error feedback (driven by the host's AppState flows)
        if (state.isLoading) {
            item(key = "progress") {
                M3EWavyLinearProgress(progress = 0.5f)
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            item(key = "error") {
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
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error
                        )
                    }
                }
            }
        }

        // 6. Classrooms Listing
        if (state.filteredClassrooms.isEmpty()) {
            item {
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOW,
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
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
        } else {
            items(state.filteredClassrooms) { room ->
                ClassroomSlotCard(room = room)
            }
        }

        // Bottom Spacing for floating navigation bar clearance (140dp)
        item(key = "bottom_spacer") {
            Spacer(Modifier.height(140.dp))
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

            // Status message & Capacity
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.People,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${room.capacity} seats",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
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
