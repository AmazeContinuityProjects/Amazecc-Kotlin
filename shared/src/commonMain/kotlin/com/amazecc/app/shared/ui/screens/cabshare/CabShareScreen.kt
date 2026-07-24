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
import com.amazecc.app.shared.model.CabJoinRequest
import com.amazecc.app.shared.model.CabJoinRequestsRes
import com.amazecc.app.shared.model.CabTrip
import com.amazecc.app.shared.model.CabTripsRes
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.ui.components.bouncySpring

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
            showSyncButton = true,
            onRefresh = AppState::refreshCabShare
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeSubTab == tab
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else 1f,
                        animationSpec = bouncySpring()
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else colors.surface)
                            .border(
                                1.dp,
                                if (isSelected) colors.accent else colors.border,
                                CircleShape
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { activeSubTab = tab }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = if (isSelected) colors.background else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
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
    val trips by AppState.cabTrips.collectAsState()
    val cabLoading by AppState.cabLoading.collectAsState()

    var fromText by remember { mutableStateOf("VIT Chennai") }
    var toText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<CabTripsRes?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmazeTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = "Destination",
                    placeholder = "e.g. Chennai Airport, Railway Station"
                )
                AmazeTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = "From",
                    placeholder = "e.g. VIT Chennai"
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
                    text = if (cabLoading) "Searching..." else "Search Rides",
                    onClick = {
                        hasSearched = true
                        AppState.searchCabTrips(fromText, toText, dateText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cabLoading,
                    icon = Icons.Rounded.Search
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (hasSearched) {
            val data = if (cabLoading) snapshot else trips.also { snapshot = it }

            if (data != null && data.success && data.trips.isNotEmpty()) {
                Text(
                    text = "Found ${data.trips.size} trips to $toText",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                data.trips.forEach { trip ->
                    CabTripCard(trip = trip)
                }
            } else if (!cabLoading) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (data?.success == false && data.message != null) data.message
                            else "No rides found. Try a different destination or date.",
                            style = AmazeTheme.typography.body.copy(color = colors.textSecondary)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Searching...", style = AmazeTheme.typography.body.copy(color = colors.textMuted))
                }
            }
        }
    }
}

@Composable
fun CabTripCard(trip: CabTrip) {
    val colors = AmazeTheme.colors
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinSeats by remember { mutableIntStateOf(1) }
    var joinMessage by remember { mutableStateOf<String?>(null) }

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
                        if (trip.driverRating != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(trip.driverRating, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                            }
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
                    Text(trip.time, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(trip.fare, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                    Text(" per seat", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                }
            }

            if (trip.vehicleModel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildString {
                        append(trip.vehicleColor ?: "")
                        if (trip.vehicleColor != null && trip.vehicleModel != null) append(" ")
                        append(trip.vehicleModel ?: "")
                        if (trip.vehiclePlate != null) append(" · $trip.vehiclePlate")
                    },
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            AmazeButton(
                text = "Request to Join",
                onClick = { showJoinDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = trip.seatsAvailable > 0
            )
        }
    }

    if (showJoinDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showJoinDialog = false; joinMessage = null },
            title = { Text("Join Trip", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("How many seats do you need?", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.border)
                                .clickable(enabled = joinSeats > 1) { joinSeats-- },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = null, tint = if (joinSeats > 1) colors.textPrimary else colors.textMuted)
                        }
                        Text("$joinSeats", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.border)
                                .clickable(enabled = joinSeats < trip.seatsAvailable && joinSeats < 4) { joinSeats++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = if (joinSeats < trip.seatsAvailable && joinSeats < 4) colors.textPrimary else colors.textMuted)
                        }
                    }
                    val jm = joinMessage
                    if (jm != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(jm, style = AmazeTheme.typography.smallLabel.copy(color = if (jm.contains("sent", ignoreCase = true)) colors.success else colors.danger))
                    }
                }
            },
            confirmButton = {
                AmazeButton("Send Request", onClick = {
                    AppState.requestJoinTrip(trip.id, joinSeats) { success, msg ->
                        joinMessage = if (success) "Request sent to ${trip.driverName}!"
                        else msg
                    }
                })
            },
            dismissButton = {
                Text("Cancel", style = AmazeTheme.typography.body.copy(color = colors.textSecondary), modifier = Modifier.clickable { showJoinDialog = false; joinMessage = null })
            },
            containerColor = colors.surface
        )
    }
}

@Composable
fun CreateTripTab() {
    val colors = AmazeTheme.colors
    val cabLoading by AppState.cabLoading.collectAsState()

    var fromText by remember { mutableStateOf("VIT Chennai") }
    var toText by remember { mutableStateOf("") }
    var tripDate by remember { mutableStateOf("") }
    var departureTime by remember { mutableStateOf("") }
    var seatsAvailable by remember { mutableIntStateOf(3) }
    var farePerPerson by remember { mutableStateOf("") }
    var carModel by remember { mutableStateOf("") }
    var carColor by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var showVehicleFields by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

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
                    placeholder = "e.g. VIT Chennai"
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

        val sm = statusMessage
        if (sm != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = sm,
                style = AmazeTheme.typography.body.copy(
                    color = if (isSuccess) colors.success else colors.danger,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AmazeButton(
            text = if (cabLoading) "Publishing..." else "Publish Trip",
            onClick = {
                statusMessage = null
                if (toText.isBlank() || tripDate.isBlank() || departureTime.isBlank() || farePerPerson.isBlank()) {
                    statusMessage = "Please fill in all required fields"
                    isSuccess = false
                    return@AmazeButton
                }
                AppState.createCabTrip(
                    from = fromText, to = toText, date = tripDate, time = departureTime,
                    seats = seatsAvailable, fare = "₹$farePerPerson",
                    vehicleModel = carModel.ifBlank { null },
                    vehicleColor = carColor.ifBlank { null },
                    vehiclePlate = plateNumber.ifBlank { null },
                    onSuccess = { tripId ->
                        statusMessage = "Trip published! ID: $tripId"
                        isSuccess = true
                        toText = ""; tripDate = ""; departureTime = ""; farePerPerson = ""
                        carModel = ""; carColor = ""; plateNumber = ""
                    },
                    onError = { msg ->
                        statusMessage = msg
                        isSuccess = false
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !cabLoading,
            icon = Icons.Rounded.DirectionsCar
        )
    }
}

@Composable
fun MyTripsTab() {
    val colors = AmazeTheme.colors
    val myTrips by AppState.myCabTrips.collectAsState()
    val joinRequests by AppState.cabJoinRequests.collectAsState()

    var selectedSegment by remember { mutableStateOf("Ongoing") }
    val segments = listOf("Ongoing", "History")

    val ongoing = myTrips?.trips?.filter { it.status != "Completed" && it.status != "Cancelled" } ?: emptyList()
    val history = myTrips?.trips?.filter { it.status == "Completed" || it.status == "Cancelled" } ?: emptyList()

    LaunchedEffect(Unit) {
        AppState.refreshMyCabTrips()
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
            if (ongoing.isNotEmpty()) {
                ongoing.forEach { trip ->
                    MyTripCard(trip = trip, tripJoinRequests = joinRequests[trip.id])
                }
            }

            if (ongoing.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No ongoing trips.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    }
                }
            }
        } else {
            if (history.isNotEmpty()) {
                history.forEach { trip ->
                    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${trip.from} → ${trip.to}",
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                )
                                AmazeBadge(
                                    text = trip.status.uppercase(),
                                    variant = if (trip.status == "Completed") BadgeVariant.SUCCESS else BadgeVariant.DANGER
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("${trip.date} · ${trip.time} · ${trip.fare}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
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

@Composable
fun MyTripCard(trip: CabTrip, tripJoinRequests: CabJoinRequestsRes?) {
    val colors = AmazeTheme.colors
    var showRequests by remember { mutableStateOf(false) }

    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${trip.from} → ${trip.to}",
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Text("${trip.date} · ${trip.time}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                }
                AmazeBadge(
                    text = trip.status.uppercase(),
                    variant = when (trip.status) {
                        "Scheduled" -> BadgeVariant.INFO
                        "Full" -> BadgeVariant.WARNING
                        else -> BadgeVariant.INFO
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Seats: ${trip.seatsAvailable}/${trip.seatsTotal}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                Text(trip.fare, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            }

            if (trip.isOwnTrip && trip.status == "Scheduled") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (showRequests) "Hide Requests" else "View Join Requests",
                    style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable {
                        showRequests = !showRequests
                        if (showRequests) {
                            AppState.refreshJoinRequests(trip.id)
                        }
                    }
                )

                if (showRequests && tripJoinRequests != null && tripJoinRequests.requests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    for (req in tripJoinRequests.requests) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(req.requesterName, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text("${req.seats} seat${if (req.seats != 1) "s" else ""} · ${req.status}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                            }
                            if (req.status == "Pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.success.copy(alpha = 0.15f)).clickable {
                                            AppState.acceptJoinRequest(trip.id, req.id)
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = "Accept", tint = colors.success, modifier = Modifier.size(18.dp))
                                    }
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.danger.copy(alpha = 0.15f)).clickable {
                                            AppState.rejectJoinRequest(trip.id, req.id)
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Reject", tint = colors.danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                } else if (showRequests) {
                    Text("No join requests yet.", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                }
            }
        }
    }
}
