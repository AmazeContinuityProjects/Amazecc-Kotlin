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
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
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
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedContainerColor = colors.surfaceContainerLow,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant
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
                            .padding(32.dp),
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
    }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
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
