package com.amazecc.app.shared.ui.screens.cabshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun CabShareScreen() {
    val colors = AmazeTheme.colors
    var activeSubTab by remember { mutableStateOf("Find Ride") }
    val tabs = listOf("Find Ride", "Create Trip", "My Trips")

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Cab Share",
            description = "Find or offer rides to airport, railway station, etc.",
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

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                when (activeSubTab) {
                    "Find Ride" -> FindRideTab()
                    "Create Trip" -> CreateTripTab()
                    "My Trips" -> MyTripsTab()
                }
            }
        }
    }
}

@Composable
fun FindRideTab() {
    val colors = AmazeTheme.colors
    val destinations = listOf("Airport", "Railway Station", "Bus Stand", "City Center")
    var selectedDestination by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }

    val mockTrips = remember {
        listOf(
            TripResult("S. Rajan", "4.8", "2:00 PM", 2, "₹250", "White Toyota Etios · TN 01 AB 1234"),
            TripResult("Priya K.", "4.9", "3:30 PM", 3, "₹200", "Blue Honda City · TN 22 CD 5678"),
            TripResult("Arun M.", "4.7", "5:00 PM", 1, "₹300", "Silver Maruti Swift · TN 07 EF 9012"),
            TripResult("Deepa R.", "4.6", "6:15 PM", 4, "₹180", "Red Hyundai i10 · TN 11 GH 3456")
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmazeDropdown(
                    options = destinations,
                    selectedOption = selectedDestination.ifEmpty { "Select destination" },
                    onOptionSelected = { selectedDestination = it },
                    label = "Destination",
                    displayMapper = { it }
                )
                AmazeTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = "Travel Date",
                    placeholder = "e.g. 2026-07-15",
                    leadingIcon = {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                )
                AmazeButton(
                    text = "Search Rides",
                    onClick = { hasSearched = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Search
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (hasSearched && selectedDestination.isNotEmpty()) {
            Text(
                text = "Found ${mockTrips.size} trips to $selectedDestination",
                style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            mockTrips.forEach { trip ->
                AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(trip.driverName, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(trip.rating, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                                    }
                                }
                            }
                            AmazeBadge("${trip.seatsAvailable} SEAT${if (trip.seatsAvailable != 1) "S" else ""} LEFT", variant = if (trip.seatsAvailable <= 1) BadgeVariant.DANGER else BadgeVariant.WARNING)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(trip.departureTime, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(trip.fare, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                Text(" per seat", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(trip.vehicleInfo, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))

                        Spacer(modifier = Modifier.height(12.dp))
                        AmazeButton("Request to Join", onClick = {}, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else if (hasSearched) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Please select a destination to search.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                }
            }
        }
    }
}

@Composable
fun CreateTripTab() {
    val colors = AmazeTheme.colors
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var tripDate by remember { mutableStateOf("") }
    var departureTime by remember { mutableStateOf("") }
    var seatsAvailable by remember { mutableStateOf(1) }
    var farePerPerson by remember { mutableStateOf("") }
    var carModel by remember { mutableStateOf("") }
    var carColor by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var showVehicleFields by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Publish a Ride",
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
        )
        Text(
            text = "Fill in the details below to share your cab with others.",
            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
        )
        Spacer(modifier = Modifier.height(16.dp))

        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmazeTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = "From",
                    placeholder = "e.g. SRM University"
                )
                AmazeTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = "To",
                    placeholder = "e.g. Chennai Airport (MAA)"
                )
                AmazeTextField(
                    value = tripDate,
                    onValueChange = { tripDate = it },
                    label = "Date",
                    placeholder = "e.g. 2026-07-20",
                    leadingIcon = {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                )
                AmazeTextField(
                    value = departureTime,
                    onValueChange = { departureTime = it },
                    label = "Departure Time",
                    placeholder = "e.g. 2:00 PM",
                    leadingIcon = {
                        Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                )

                Column {
                    Text(
                        text = "Seats Available",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.border)
                                .clickable(enabled = seatsAvailable > 1) { seatsAvailable-- },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease", tint = if (seatsAvailable > 1) colors.textPrimary else colors.textMuted, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = "$seatsAvailable",
                            style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.border)
                                .clickable(enabled = seatsAvailable < 6) { seatsAvailable++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase", tint = if (seatsAvailable < 6) colors.textPrimary else colors.textMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                AmazeTextField(
                    value = farePerPerson,
                    onValueChange = { farePerPerson = it },
                    label = "Fare per Person (₹)",
                    placeholder = "e.g. 250"
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (showVehicleFields) "Vehicle Details (Optional)" else "Add Vehicle Details",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable { showVehicleFields = !showVehicleFields }
                )

                if (showVehicleFields) {
                    AmazeTextField(
                        value = carModel,
                        onValueChange = { carModel = it },
                        label = "Car Model",
                        placeholder = "e.g. Toyota Etios"
                    )
                    AmazeTextField(
                        value = carColor,
                        onValueChange = { carColor = it },
                        label = "Color",
                        placeholder = "e.g. White"
                    )
                    AmazeTextField(
                        value = plateNumber,
                        onValueChange = { plateNumber = it },
                        label = "License Plate",
                        placeholder = "e.g. TN 01 AB 1234"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AmazeButton(
            text = "Publish Trip",
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.DirectionsCar
        )
    }
}

@Composable
fun MyTripsTab() {
    val colors = AmazeTheme.colors
    var selectedSegment by remember { mutableStateOf("Ongoing") }
    val segments = listOf("Ongoing", "History")

    val ongoingTrips = remember {
        listOf(
            UserTrip("Chennai Airport", "Jul 15, 2026", "2:00 PM", 2, "Scheduled", false),
            UserTrip("Railway Station", "Jul 16, 2026", "8:00 AM", 0, "Full", false)
        )
    }

    val historyTrips = remember {
        listOf(
            UserTrip("Bus Stand", "Jul 10, 2026", "10:00 AM", 3, "Completed", true),
            UserTrip("City Center", "Jul 5, 2026", "4:00 PM", 1, "Cancelled", true)
        )
    }

    val matchRequests = remember {
        listOf(
            MatchRequest("Vikram S.", "2 seats", "Railway Station · Jul 16"),
            MatchRequest("Neha P.", "1 seat", "Chennai Airport · Jul 16")
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            segments.forEach { seg ->
                val isSelected = selectedSegment == seg
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .clickable { selectedSegment = seg }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = seg,
                        style = AmazeTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) colors.background else colors.textSecondary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSegment == "Ongoing") {
            if (ongoingTrips.isNotEmpty()) {
                ongoingTrips.forEach { trip ->
                    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(trip.destination, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                AmazeBadge(
                                    text = trip.status.uppercase(),
                                    variant = when (trip.status) {
                                        "Scheduled" -> BadgeVariant.INFO
                                        "Full" -> BadgeVariant.WARNING
                                        "Cancelled" -> BadgeVariant.DANGER
                                        else -> BadgeVariant.INFO
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(trip.date, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                                    Text(trip.time, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Seats left", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                                    Text(
                                        "${trip.seatsLeft}",
                                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = if (trip.seatsLeft == 0) colors.danger else colors.textPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (matchRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Match Requests",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))

                matchRequests.forEach { req ->
                    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(req.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text(req.tripInfo, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                    }
                                }
                                Text(req.seats, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AmazeButton(
                                    text = "Accept",
                                    onClick = { },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.Check
                                )
                                AmazeButton(
                                    text = "Reject",
                                    onClick = { },
                                    modifier = Modifier.weight(1f),
                                    variant = ButtonVariant.SECONDARY,
                                    icon = Icons.Rounded.Close
                                )
                            }
                        }
                    }
                }
            }

            if (ongoingTrips.isEmpty() && matchRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No ongoing trips.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    }
                }
            }
        } else {
            if (historyTrips.isNotEmpty()) {
                historyTrips.forEach { trip ->
                    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(trip.destination, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                AmazeBadge(
                                    text = trip.status.uppercase(),
                                    variant = if (trip.status == "Completed") BadgeVariant.SUCCESS else BadgeVariant.DANGER
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("${trip.date} · ${trip.time}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No trip history.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    }
                }
            }
        }
    }
}

private data class TripResult(
    val driverName: String,
    val rating: String,
    val departureTime: String,
    val seatsAvailable: Int,
    val fare: String,
    val vehicleInfo: String
)

private data class UserTrip(
    val destination: String,
    val date: String,
    val time: String,
    val seatsLeft: Int,
    val status: String,
    val isHistory: Boolean
)

private data class MatchRequest(
    val name: String,
    val seats: String,
    val tripInfo: String
)
