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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import com.amazecc.app.shared.domain.overhaul.M3EBusPlacement
import com.amazecc.app.shared.domain.overhaul.M3EBusRoute
import com.amazecc.app.shared.domain.overhaul.M3EBusStop
import com.amazecc.app.shared.domain.overhaul.M3ETransportAction
import com.amazecc.app.shared.domain.overhaul.M3ETransportFilter
import com.amazecc.app.shared.domain.overhaul.M3ETransportState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Campus & Dayscholar Transport Hub.
 * 100% Stateless: accepts [M3ETransportState] and emits [M3ETransportAction].
 * Features route search, AC/Dispersal filters, driver contact, and expandable stops timeline.
 */
@Composable
fun M3ETransportScreen(
    state: M3ETransportState,
    onAction: (M3ETransportAction) -> Unit,
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
                        text = "Campus Transit",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Buses, Shuttles & Route Schedules",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onAction(M3ETransportAction.Refresh) },
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
                onValueChange = { onAction(M3ETransportAction.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search route, stop, or driver...",
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

        // 3. Filter Segment
        item {
            M3EConnectedButtonGroup(
                items = M3ETransportFilter.entries,
                selectedItem = state.activeFilter,
                onItemSelected = { onAction(M3ETransportAction.SelectFilter(it)) },
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. Routes Counter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bus Routes (${state.filteredRoutes.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                Text(
                    text = "${state.routes.count { it.isAc }} AC • ${state.routes.count { !it.isAc }} Non-AC",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary
                )
            }
        }

        // 5. Routes Listing
        if (state.filteredRoutes.isEmpty()) {
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
                            imageVector = Icons.Rounded.DirectionsBus,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No routes match your search or filter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.filteredRoutes) { route ->
                BusRouteCard(
                    route = route,
                    isExpanded = state.expandedRouteId == route.id,
                    onToggleExpand = { onAction(M3ETransportAction.ToggleExpandRoute(route.id)) },
                    onCallDriver = { onAction(M3ETransportAction.CallDriver(route.driverPhone)) },
                    onOpenWhatsapp = {
                        route.whatsappGroup?.let { group ->
                            onAction(M3ETransportAction.OpenWhatsappGroup(group))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BusRouteCard(
    route: M3EBusRoute,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCallDriver: () -> Unit,
    onOpenWhatsapp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = if (isExpanded) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.LOW,
        cornerRadius = 22.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Route Number & Type Badge
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
                            .background(colors.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = route.routeNumber,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimaryContainer
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (route.isAc) colors.tertiaryContainer else colors.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (route.isAc) "AC COACH" else "NON-AC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = if (route.isAc) colors.onTertiaryContainer else colors.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = route.routeName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Driver: ${route.driverName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )

                if (!route.busLocation.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = route.busLocation,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.primary
                        )
                    }
                }
            }

            // Expanded stops & placements view
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Call Driver / WhatsApp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ElevatedButton(
                        onClick = onCallDriver,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Call Driver",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (!route.whatsappGroup.isNullOrBlank()) {
                        ElevatedButton(
                            onClick = onOpenWhatsapp,
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.surfaceContainerHigh,
                                contentColor = colors.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Route Chat",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Stops Timeline
                if (route.stops.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Pickup Stops (${route.stops.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        route.stops.forEach { stop ->
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
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(colors.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${stop.stopOrder}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = stop.stopName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface
                                    )
                                }

                                Text(
                                    text = stop.pickupTime,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.primary
                                )
                            }
                        }
                    }
                }

                // Dispersal Zones
                if (route.placements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Evening Dispersal Bays",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        route.placements.forEach { placement ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceContainerHigh)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${placement.dispersalTime}: ${placement.zone}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = colors.onSurface
                                )
                            }
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
fun M3ETransportScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ETransportScreen(
            state = createMockTransportState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3ETransportScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ETransportScreen(
            state = createMockTransportState(),
            onAction = {}
        )
    }
}

private fun createMockTransportState(): M3ETransportState {
    val sampleRoutes = listOf(
        M3EBusRoute(
            id = 1,
            routeNumber = "Route 12",
            routeName = "Tambaram Sanatorium to Campus via Chromepet",
            isAc = true,
            driverName = "K. Murugan",
            driverPhone = "+91 98401 23456",
            whatsappGroup = "https://chat.whatsapp.com/sample12",
            busLocation = "Near TT Gate",
            stops = listOf(
                M3EBusStop(stopOrder = 1, stopName = "Tambaram Sanatorium", pickupTime = "06:45 AM"),
                M3EBusStop(stopOrder = 2, stopName = "Chromepet Station", pickupTime = "06:55 AM"),
                M3EBusStop(stopOrder = 3, stopName = "Pallavaram Bus Stand", pickupTime = "07:05 AM"),
                M3EBusStop(stopOrder = 4, stopName = "VIT Campus Main Gate", pickupTime = "07:45 AM")
            ),
            placements = listOf(
                M3EBusPlacement(dispersalTime = "5:00 PM", zone = "Bay 4 (SJT Gate)"),
                M3EBusPlacement(dispersalTime = "6:00 PM", zone = "Bay 7 (Main Gate)")
            )
        ),
        M3EBusRoute(
            id = 2,
            routeNumber = "Route 24",
            routeName = "Vellore New Bus Stand to SJT Roundabout",
            isAc = false,
            driverName = "P. Selvaraj",
            driverPhone = "+91 98402 34567",
            whatsappGroup = null,
            busLocation = "Katpadi Station Road",
            stops = listOf(
                M3EBusStop(stopOrder = 1, stopName = "New Bus Stand", pickupTime = "07:15 AM"),
                M3EBusStop(stopOrder = 2, stopName = "Old Katpadi Bridge", pickupTime = "07:30 AM"),
                M3EBusStop(stopOrder = 3, stopName = "Campus North Gate", pickupTime = "07:45 AM")
            ),
            placements = listOf(
                M3EBusPlacement(dispersalTime = "5:00 PM", zone = "Bay 2")
            )
        )
    )

    return M3ETransportState(
        routes = sampleRoutes,
        filteredRoutes = sampleRoutes,
        searchQuery = "",
        activeFilter = M3ETransportFilter.ALL,
        expandedRouteId = 1
    )
}
