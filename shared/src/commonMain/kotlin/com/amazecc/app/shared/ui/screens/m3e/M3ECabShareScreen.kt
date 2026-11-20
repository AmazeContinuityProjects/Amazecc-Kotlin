package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3ECabShareAction
import com.amazecc.app.shared.domain.overhaul.M3ECabShareFilter
import com.amazecc.app.shared.domain.overhaul.M3ECabShareHub
import com.amazecc.app.shared.domain.overhaul.M3ECabShareRide
import com.amazecc.app.shared.domain.overhaul.M3ECabShareState
import com.amazecc.app.shared.domain.overhaul.M3EGenderPreference
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Cab Share & Travel Pool Hub.
 * 100% Stateless: receives [M3ECabShareState] and emits [M3ECabShareAction].
 * Features hub destinations, airport/railway splitting filters, ride join flow, and creator contact.
 */
@Composable
fun M3ECabShareScreen(
    state: M3ECabShareState,
    onAction: (M3ECabShareAction) -> Unit,
    modifier: Modifier = Modifier,
    /** Receives a validated ride draft; the host performs the real create request. */
    onCreateRide: (M3ECabRideDraft) -> Unit = {},
    /** Pending join requests on the current user's trips (from the real `requests` payload). */
    pendingMatches: List<M3ECabMatchUi> = emptyList(),
    /** D-039: accept/reject a match through `AppState.cabHandleMatchAction`. */
    onMatchAction: (matchId: Long, action: String) -> Unit = { _, _ -> }
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar with Create Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cab Share",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Split rides to Airport & Railway",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElevatedButton(
                        onClick = { onAction(M3ECabShareAction.OpenCreateDialog) },
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offer Ride",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3ECabShareAction.Refresh) },
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
        }

        // Create/join feedback is a real result from the host, never a fabricated success.
        state.errorMessage?.let { message ->
            item {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.LOW
                ) {
                    Text(text = message, color = colors.onSurface, fontSize = 13.sp)
                }
            }
        }

        // 1b. Pending match requests on the student's own trips (D-039).
        if (pendingMatches.isNotEmpty()) {
            item {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.HIGH,
                    cornerRadius = 20.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Match requests (${pendingMatches.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        pendingMatches.forEach { match ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        match.requesterName.ifBlank { "A student" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.onSurface
                                    )
                                    Text(
                                        "wants to join trip ${match.tripId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { onMatchAction(match.matchId, "accept") },
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.primaryContainer)
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = "Accept match", tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { onMatchAction(match.matchId, "reject") },
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceContainerHigh)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Reject match", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Popular Transit Hubs Strip
        if (state.hubs.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(state.hubs) { hub ->
                        HubChip(
                            hub = hub,
                            onClick = { onAction(M3ECabShareAction.SearchQueryChanged(hub.name)) }
                        )
                    }
                }
            }
        }

        // 3. Search Bar
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onAction(M3ECabShareAction.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search destination, date, or student...",
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

        // 4. Filter Button Group
        item {
            M3EConnectedButtonGroup(
                items = M3ECabShareFilter.entries,
                selectedItem = state.activeFilter,
                onItemSelected = { onAction(M3ECabShareAction.SelectFilter(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 5. Rides Count Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Pools (${state.rides.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                Text(
                    text = "${state.rides.sumOf { it.availableSeats }} seats open",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary
                )
            }
        }

        // 6. Rides Listing
        if (state.rides.isEmpty()) {
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
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No cab share pools found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.rides) { ride ->
                CabShareRideCard(
                    ride = ride,
                    onJoin = { onAction(M3ECabShareAction.JoinTrip(ride.tripId)) },
                    onContact = {
                        ride.creatorPhone?.let { phone ->
                            onAction(M3ECabShareAction.ContactCreator(phone))
                        }
                    }
                )
            }
        }

        // Bottom Spacing for floating navigation bar clearance (140dp)
        item(key = "bottom_spacer") {
            Spacer(Modifier.height(140.dp))
        }
    }

        if (state.isCreateDialogOpen) {
            M3ECreateRideDialog(
                hubs = state.hubs,
                onDismiss = { onAction(M3ECabShareAction.CloseCreateDialog) },
                onCreate = onCreateRide
            )
        }
    }
}

/** One pending join request on a trip the student owns. */
data class M3ECabMatchUi(
    val matchId: Long,
    val requesterName: String,
    val tripId: String
)

/** Draft carrying only values the real `cabCreateTripNew` endpoint accepts. */
data class M3ECabRideDraft(
    val fromHubId: String = "",
    val toHubId: String = "",
    val date: String = "",
    val time: String = "",
    val tolerance: String = "1",
    val seats: String = "3",
    val gender: String = "any",
    val notes: String = ""
)

@Composable
private fun M3ECreateRideDialog(
    hubs: List<M3ECabShareHub>,
    onDismiss: () -> Unit,
    onCreate: (M3ECabRideDraft) -> Unit
) {
    val colors = M3ETheme.colors
    var draft by remember { mutableStateOf(M3ECabRideDraft()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offer a ride", color = colors.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hubs.isEmpty()) {
                    Text(
                        text = "No hubs are available right now. Pull to refresh and try again.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                } else {
                    Text("From", color = colors.onSurfaceVariant, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        hubs.forEach { hub ->
                            FilterChip(
                                selected = draft.fromHubId == hub.id,
                                onClick = { draft = draft.copy(fromHubId = hub.id) },
                                label = { Text(hub.name, fontSize = 11.sp) }
                            )
                        }
                    }
                    Text("To", color = colors.onSurfaceVariant, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        hubs.forEach { hub ->
                            FilterChip(
                                selected = draft.toHubId == hub.id,
                                onClick = { draft = draft.copy(toHubId = hub.id) },
                                label = { Text(hub.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.date,
                        onValueChange = { draft = draft.copy(date = it) },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.time,
                        onValueChange = { draft = draft.copy(time = it) },
                        label = { Text("Time (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.seats,
                        onValueChange = { draft = draft.copy(seats = it.filter(Char::isDigit)) },
                        label = { Text("Seats") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.tolerance,
                        onValueChange = { draft = draft.copy(tolerance = it.filter { c -> c.isDigit() || c == '.' }) },
                        label = { Text("Tolerance (h)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(draft) },
                enabled = draft.fromHubId.isNotBlank() &&
                    draft.toHubId.isNotBlank() &&
                    draft.fromHubId != draft.toHubId &&
                    draft.date.isNotBlank() &&
                    draft.time.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HubChip(
    hub: M3ECabShareHub,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val isAirport = hub.name.contains("Airport", ignoreCase = true)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isAirport) Icons.Rounded.Flight else Icons.Rounded.Train,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = hub.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Text(
                text = hub.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CabShareRideCard(
    ride: M3ECabShareRide,
    onJoin: () -> Unit,
    onContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 22.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Destination Hub & Seats Open
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (ride.hubName.contains("Airport", ignoreCase = true)) Icons.Rounded.Flight else Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = ride.hubName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${ride.availableSeats} of ${ride.totalSeats} seats left",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time & Date Banner
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
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${ride.travelDate} at ${ride.preferredTime} (±${ride.toleranceHours}h)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface
                    )
                }

                // Gender Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = ride.genderPreference.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (!ride.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"${ride.notes}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Creator & Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Posted by ${ride.creatorName}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = ride.creatorRegNo,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!ride.creatorPhone.isNullOrBlank()) {
                        IconButton(
                            onClick = onContact,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Call",
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (ride.isJoined) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Joined",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        ElevatedButton(
                            onClick = onJoin,
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            )
                        ) {
                            Text(
                                text = "Join Pool",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
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
fun M3ECabShareScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECabShareScreen(
            state = createMockCabShareState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3ECabShareScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECabShareScreen(
            state = createMockCabShareState(),
            onAction = {}
        )
    }
}

private fun createMockCabShareState() = M3ECabShareState(
    hubs = listOf(
        M3ECabShareHub("hub_1", "Chennai Airport", "MAA Airport Split"),
        M3ECabShareHub("hub_2", "Katpadi Station", "Trains & Express"),
        M3ECabShareHub("hub_3", "Bengaluru Airport", "BLR Terminal")
    ),
    rides = listOf(
        M3ECabShareRide(
            tripId = "trip_01",
            creatorName = "Aditya Sharma",
            creatorRegNo = "22BCE1045",
            hubName = "Chennai Airport (MAA)",
            travelDate = "28 Nov 2026",
            preferredTime = "04:30 AM",
            toleranceHours = 1,
            availableSeats = 2,
            totalSeats = 4,
            genderPreference = M3EGenderPreference.ANY,
            notes = "Indigo 6E 214 flight at 08:30 AM. Looking for 2 people to split cab from SJT gate.",
            creatorPhone = "+91 98401 99999",
            isJoined = false
        ),
        M3ECabShareRide(
            tripId = "trip_02",
            creatorName = "Sneha Patel",
            creatorRegNo = "23BCI0120",
            hubName = "Katpadi Railway Station",
            travelDate = "27 Nov 2026",
            preferredTime = "08:15 PM",
            toleranceHours = 0,
            availableSeats = 1,
            totalSeats = 3,
            genderPreference = M3EGenderPreference.FEMALE_ONLY,
            notes = "Vande Bharat train at 09:20 PM. Girls only please.",
            creatorPhone = "+91 98402 88888",
            isJoined = true
        )
    ),
    activeFilter = M3ECabShareFilter.ALL,
    searchQuery = ""
)
