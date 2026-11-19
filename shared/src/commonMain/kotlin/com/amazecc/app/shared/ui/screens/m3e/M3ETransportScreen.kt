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
import com.amazecc.app.shared.domain.overhaul.M3ETransportRegistration
import com.amazecc.app.shared.domain.overhaul.M3ETransportState
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.model.TransportDataRes
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

        // 3b. The student's real bus-pass registration (VTOP /api/transport), when available.
        state.registration?.let { registration ->
            item(key = "bus_registration") {
                M3ESurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = SurfaceContainerLevel.STANDARD
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsBus,
                            contentDescription = null,
                            tint = if (registration.hasRegistration) colors.success else colors.warning,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bus Registration",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                            val status = if (registration.hasRegistration) {
                                listOfNotNull(
                                    registration.routeSelected?.takeIf { it.isNotBlank() },
                                    registration.busRouteId?.takeIf { it.isNotBlank() }?.let { "Route ID $it" },
                                    registration.registerNumber?.takeIf { it.isNotBlank() }
                                ).joinToString(" • ").ifBlank { "Bus pass active" }
                            } else {
                                "No active bus registration found"
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
                            .padding(vertical = 16.dp),
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

        // Bottom Spacing for floating navigation bar clearance (140dp)
        item(key = "bottom_spacer") {
            Spacer(Modifier.height(140.dp))
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
            modifier = Modifier.fillMaxWidth()
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
                    // Only offer a call when the route actually carries a driver number.
                    if (route.driverPhone.isNotBlank()) {
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

/**
 * The student's real bus-pass registration from `TransportDataRes` (`/api/transport`), mapped into
 * the immutable screen state. Returns null when the API has not produced a payload yet.
 */
internal fun transportRegistrationFrom(data: TransportDataRes?): M3ETransportRegistration? =
    data?.let {
        M3ETransportRegistration(
            hasRegistration = it.hasRegistration,
            registerNumber = it.registerNumber,
            name = it.name,
            branch = it.branch,
            routeSelected = it.routeSelected,
            busRouteId = it.busRouteId,
            qrCode = it.qrCode
        )
    }

/** Map the real `BusesRes` into stateless route rows; no placeholder identifiers. */
internal fun mapBusesToRoutes(res: BusesRes?): List<M3EBusRoute> =
    res?.buses.orEmpty().mapIndexed { idx, b ->
        M3EBusRoute(
            id = b.id.toIntOrNull() ?: (idx + 1),
            routeNumber = b.route,
            routeName = b.route,
            // "Non-AC" also contains "AC" — exclude it explicitly so the AC/NON_AC filters work.
            isAc = !b.type.contains("non", ignoreCase = true) && b.type.contains("ac", ignoreCase = true),
            driverName = b.driverName,
            driverPhone = b.driverPhone,
            whatsappGroup = b.whatsappGroup.takeIf { it.isNotBlank() },
            busLocation = b.busLocation.takeIf { it.isNotBlank() },
            stops = b.stops.map { s ->
                M3EBusStop(stopOrder = s.stopOrder, stopName = s.stopName, pickupTime = s.pickupTime ?: "")
            },
            placements = b.placements.map { p -> M3EBusPlacement(p.dispersalTime, p.zone) }
        )
    }

/** Parse a clock like `"05:15 PM"`, `"5PM"`, `"17:30"` into a 0-23 hour. */
internal fun parseClockHour(raw: String): Int? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val isPm = value.contains("PM", ignoreCase = true)
    val isAm = value.contains("AM", ignoreCase = true)
    val hour = Regex("(\\d{1,2})(?::\\d{2})?").find(value)?.groupValues?.get(1)?.toIntOrNull()
        ?: return null
    if (hour !in 0..23) return null
    return when {
        isPm -> if (hour == 12) 12 else hour + 12
        isAm -> if (hour == 12) 0 else hour
        else -> hour
    }
}

/**
 * Which evening dispersal batch a real route belongs to, derived from the route's own data:
 * an M3E placement when present, otherwise the evening `pickupTime` of one of its real stops.
 * Returns null for a route with no evening service - it must not appear in either evening filter.
 */
internal fun eveningBatchFor(route: M3EBusRoute): M3ETransportFilter? {
    val hours = route.placements.mapNotNull { parseClockHour(it.dispersalTime) } +
        route.stops.mapNotNull { parseClockHour(it.pickupTime) }
    val eveningHour = hours.firstOrNull { it >= 17 } ?: return null
    return if (eveningHour == 17) M3ETransportFilter.EVENING_5PM else M3ETransportFilter.EVENING_6PM
}

/** Apply a filter to a route using only real route data. */
internal fun matchesTransportFilter(route: M3EBusRoute, filter: M3ETransportFilter): Boolean =
    when (filter) {
        M3ETransportFilter.ALL -> true
        M3ETransportFilter.AC -> route.isAc
        M3ETransportFilter.NON_AC -> !route.isAc
        M3ETransportFilter.EVENING_5PM,
        M3ETransportFilter.EVENING_6PM -> eveningBatchFor(route) == filter
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
