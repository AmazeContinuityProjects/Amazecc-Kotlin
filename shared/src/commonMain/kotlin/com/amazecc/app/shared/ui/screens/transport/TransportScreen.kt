package com.amazecc.app.shared.ui.screens.transport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.model.BusItem

@Composable
fun TransportScreen() {
    val colors = AmazeTheme.colors
    val transportRes by AppState.transport.collectAsState()
    val buses = transportRes?.buses ?: emptyList()
    var activeSubTab by remember { mutableStateOf("Bus Finder") }
    val tabs = listOf("Bus Finder", "Registration")

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Transport Hub",
            description = "Dayboarder status and bus routes",
            showBackButton = false,
            showSyncButton = true
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeSubTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.accent else colors.surface)
                            .clickable { activeSubTab = tab }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold).copy(
                                color = if (isSelected) colors.background else colors.textSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                when (activeSubTab) {
                    "Bus Finder" -> BusFinderTab(buses)
                    "Registration" -> TransportRegistrationTab()
                }
            }
        }
    }
}

@Composable
fun BusFinderTab(buses: List<BusItem>) {
    val colors = AmazeTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var expandedRouteNo by remember { mutableStateOf<String?>(null) }

    val filteredBuses = remember(buses, searchQuery) {
        if (searchQuery.isBlank()) buses
        else buses.filter {
            it.routeNo.contains(searchQuery, ignoreCase = true) ||
            it.routeName.contains(searchQuery, ignoreCase = true)
        }
    }

    val mockStops = remember {
        mapOf(
            "R001" to listOf(
                Triple("VIT Main Gate", "7:30 AM", "\u20B915"),
                Triple("Gandhi Nagar", "7:45 AM", "\u20B910"),
                Triple("City Center", "8:00 AM", "\u20B912"),
                Triple("Central Station", "8:15 AM", "\u20B920")
            ),
            "R002" to listOf(
                Triple("North Campus", "7:15 AM", "\u20B910"),
                Triple("Library Junction", "7:30 AM", "\u20B98"),
                Triple("South Gate", "7:50 AM", "\u20B912"),
                Triple("Railway Station", "8:10 AM", "\u20B918")
            ),
            "R003" to listOf(
                Triple("Hostel Block", "7:00 AM", "\u20B95"),
                Triple("Academic Block", "7:20 AM", "\u20B98"),
                Triple("Sports Complex", "7:35 AM", "\u20B910"),
                Triple("Main Campus", "7:55 AM", "\u20B915")
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search routes or stops...", color = colors.textMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.accent,
                focusedLeadingIconColor = colors.accent,
                unfocusedLeadingIconColor = colors.textMuted
            )
        )

        if (filteredBuses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DirectionsBus, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No routes found", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 30.dp)
            ) {
                items(filteredBuses, key = { it.routeNo }) { bus ->
                    val isExpanded = expandedRouteNo == bus.routeNo

                    AmazeCard(
                        onClick = { expandedRouteNo = if (isExpanded) null else bus.routeNo }
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.DirectionsBus, contentDescription = null, tint = colors.accent)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(bus.routeNo, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text(bus.routeName, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AmazeBadge("Active", variant = BadgeVariant.SUCCESS)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = colors.textMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Departure: ${bus.time}", style = AmazeTheme.typography.caption.copy(color = colors.textPrimary))

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border)
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Text("Route Stops", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                                Spacer(modifier = Modifier.height(8.dp))

                                val stops = mockStops[bus.routeNo] ?: listOf(
                                    Triple("VIT Main Gate", bus.time, "\u20B915"),
                                    Triple("City Center", "8:00 AM", "\u20B912"),
                                    Triple("Central Station", "8:30 AM", "\u20B920")
                                )

                                stops.forEachIndexed { index, (stopName, stopTime, fare) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier.size(8.dp).background(colors.accent, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(stopName, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                                                Text(stopTime, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                            }
                                        }
                                        Text(fare, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                    }
                                    if (index < stops.size - 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                if (bus.driverName != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Driver: ${bus.driverName}", style = AmazeTheme.typography.caption.copy(color = colors.textPrimary))
                                        if (bus.driverPhone != null) {
                                            Text(" | ${bus.driverPhone}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransportRegistrationTab() {
    val colors = AmazeTheme.colors
    var passStatus by remember { mutableStateOf("inactive") }
    var selectedRoute by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf("") }
    var routeExpanded by remember { mutableStateOf(false) }
    var semesterExpanded by remember { mutableStateOf(false) }

    val routes = listOf("R001 - VIT Main Campus", "R002 - City Center", "R003 - Railway Station")
    val semesters = listOf("Fall 2025", "Spring 2026", "Fall 2026")

    val history = remember {
        listOf(
            Triple("Fall 2025", "R002 - City Center", "Expired"),
            Triple("Spring 2025", "R001 - VIT Main Campus", "Expired")
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            val (statusBadge, statusIcon) = when (passStatus) {
                "active" -> "Active" to Icons.Rounded.CheckCircle
                "expired" -> "Expired" to Icons.Rounded.Cancel
                else -> "Inactive" to Icons.Rounded.Info
            }
            val badgeVariant = when (passStatus) {
                "active" -> BadgeVariant.SUCCESS
                "expired" -> BadgeVariant.DANGER
                else -> BadgeVariant.WARNING
            }

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.size(60.dp).background(colors.accent.copy(alpha = 0.1f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Transport Pass", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))

                    if (passStatus == "active") {
                        AmazeBadge("Active", modifier = Modifier.padding(top = 8.dp), variant = BadgeVariant.SUCCESS)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Valid until: Dec 2026", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    } else if (passStatus == "expired") {
                        AmazeBadge("Expired", modifier = Modifier.padding(top = 8.dp), variant = BadgeVariant.DANGER)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your pass has expired. Apply for a new one below.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), textAlign = TextAlign.Center)
                    } else {
                        AmazeBadge("Inactive", modifier = Modifier.padding(top = 8.dp), variant = BadgeVariant.WARNING)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No active transport pass found. Apply below.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        if (passStatus != "active") {
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Apply for Transport Pass", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Select Route", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedTextField(
                                value = selectedRoute,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Choose a route", color = colors.textMuted) },
                                trailingIcon = {
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.textMuted)
                                },
                                modifier = Modifier.fillMaxWidth().clickable { routeExpanded = true },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.border,
                                    cursorColor = colors.accent
                                )
                            )
                            DropdownMenu(
                                expanded = routeExpanded,
                                onDismissRequest = { routeExpanded = false }
                            ) {
                                routes.forEach { route ->
                                    DropdownMenuItem(
                                        text = { Text(route, color = colors.textPrimary) },
                                        onClick = {
                                            selectedRoute = route
                                            routeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Select Semester", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedTextField(
                                value = selectedSemester,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Choose semester", color = colors.textMuted) },
                                trailingIcon = {
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.textMuted)
                                },
                                modifier = Modifier.fillMaxWidth().clickable { semesterExpanded = true },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.border,
                                    cursorColor = colors.accent
                                )
                            )
                            DropdownMenu(
                                expanded = semesterExpanded,
                                onDismissRequest = { semesterExpanded = false }
                            ) {
                                semesters.forEach { semester ->
                                    DropdownMenuItem(
                                        text = { Text(semester, color = colors.textPrimary) },
                                        onClick = {
                                            selectedSemester = semester
                                            semesterExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        AmazeButton(
                            text = "Submit Application",
                            onClick = { passStatus = "active" },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedRoute.isNotBlank() && selectedSemester.isNotBlank()
                        )
                    }
                }
            }
        }

        item {
            Text("Registration History", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }

        items(history) { (semester, route, status) ->
            val variant = when (status) {
                "Active" -> BadgeVariant.SUCCESS
                "Expired" -> BadgeVariant.DANGER
                else -> BadgeVariant.INFO
            }
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(semester, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                        Text(route, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    AmazeBadge(status, variant = variant)
                }
            }
        }
    }
}
