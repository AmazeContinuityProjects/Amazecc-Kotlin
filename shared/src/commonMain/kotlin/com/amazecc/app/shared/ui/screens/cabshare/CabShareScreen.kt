package com.amazecc.app.shared.ui.screens.cabshare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings
import kotlinx.datetime.toLocalDateTime

@Composable
fun CabShareScreen() {
    val colors = AmazeTheme.colors
    val cabShareUser by AppState.cabShareUser.collectAsState()

    if (cabShareUser == null) {
        CabShareAuthGate()
    } else {
        CabShareContent()
    }
}

@Composable
fun CabShareAuthGate() {
    val colors = AmazeTheme.colors
    val authLoading by AppState.cabShareAuthLoading.collectAsState()
    var username by remember { mutableStateOf(SettingsManager.getCredentials()?.first ?: "") }
    var vtopPassword by remember { mutableStateOf(SettingsManager.getCredentials()?.second ?: "") }
    var phoneNumber by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Cab Share",
                description = "Verify VTOP + phone to get started",
                showBackButton = false,
                showSyncButton = false
            )
            HeaderSpacer()
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(AmazeTheme.radius.large)).background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sectionGap))
                Text("Start using Cab Share", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                Text("Verify your VTOP account and add a reachable phone number.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))

                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmazeTextField(value = username, onValueChange = { username = it; error = null }, label = "User ID", placeholder = "VTOP login User ID",
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) })
                        AmazeTextField(value = vtopPassword, onValueChange = { vtopPassword = it; error = null }, label = "VTOP Password", placeholder = "Enter VTOP password",
                            leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) })
                        AmazeTextField(value = phoneNumber, onValueChange = { phoneNumber = it; error = null }, label = "Phone Number", placeholder = "10-digit mobile number",
                            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) })

                        if (error != null) {
                            Text(error!!, style = AmazeTheme.typography.smallLabel.copy(color = colors.danger, fontWeight = FontWeight.Bold))
                        }
                        AmazeButton(
                            text = if (authLoading) "Verifying..." else "Authenticate & Continue",
                            onClick = {
                                val cleanPhone = phoneNumber.trim()
                                val cleanUser = username.trim()
                                val cleanPass = vtopPassword.trim()
                                if (cleanUser.isBlank() || cleanPass.isBlank()) {
                                    error = "Enter VTOP credentials"
                                    return@AmazeButton
                                }
                                if (cleanPhone.length < 10) {
                                    error = "Enter a valid 10-digit phone number"
                                    return@AmazeButton
                                }
                                AppState.cabShareLogin(cleanUser, cleanPass, cleanPhone) { success, msg ->
                                    if (!success) error = msg
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !authLoading && username.isNotBlank() && vtopPassword.isNotBlank() && phoneNumber.isNotBlank(),
                            icon = Icons.Rounded.Star
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CabShareContent() {
    val colors = AmazeTheme.colors
    var activeSubTab by remember { mutableStateOf("Find Ride") }
    val tabs = listOf("Find Ride", "Create Trip", "My Trips")

    LaunchedEffect(Unit) {
        AppState.fetchCabHubs()
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Cab Share",
            description = "Find or offer rides",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = { AppState.cabRefreshMyTripsNew() }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSpacer()
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = activeSubTab == tab
                    Box(
                        modifier = Modifier.clip(CircleShape).background(if (isSelected) colors.accent else colors.surface)
                            .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                            .clickable { activeSubTab = tab }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tab, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) colors.background else colors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
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
    val hubs by AppState.cabHubs.collectAsState()
    val cabLoading by AppState.cabLoading.collectAsState()

    var fromHubId by remember { mutableStateOf("") }
    var toHubId by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var trips by remember { mutableStateOf<List<CabShareTrip>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showPendingModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
        date = today
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("From", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
                        SelectHubField(value = fromHubId, onValueChange = { v ->
                            if (v == toHubId && hubs.size > 1) toHubId = hubs.first { it.hub_id.toString() != v }.hub_id.toString()
                            fromHubId = v
                        }, hubs = hubs, placeholder = "Any")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("To", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
                        SelectHubField(value = toHubId, onValueChange = { v ->
                            if (v == fromHubId && hubs.size > 1) fromHubId = hubs.first { it.hub_id.toString() != v }.hub_id.toString()
                            toHubId = v
                        }, hubs = hubs, placeholder = "Any")
                    }
                }
                AmazeTextField(
                    value = date, onValueChange = { date = it },
                    label = "Travel Date", placeholder = "YYYY-MM-DD",
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) }
                )
                AmazeButton(
                    text = if (cabLoading) "Searching..." else "Search Rides",
                    onClick = {
                        message = null
                        hasSearched = true
                        val from = fromHubId.toIntOrNull()
                        val to = toHubId.toIntOrNull()
                        if (from != null && to != null && from == to) {
                            message = Pair("From and To cannot be the same.", false)
                            return@AmazeButton
                        }
                        AppState.cabSearchTripsNew(from, to, date) { result ->
                            trips = result
                            if (result.isEmpty() && hasSearched) {
                                message = Pair("No rides found. Try a different hub or date.", false)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cabLoading,
                    icon = Icons.Rounded.Search
                )
            }
        }

        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        message?.let { (msg, isSuccess) ->
            Text(msg, style = AmazeTheme.typography.body.copy(color = if (isSuccess) colors.success else colors.danger, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
        }

        if (hasSearched) {
            if (cabLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Searching...", style = AmazeTheme.typography.body.copy(color = colors.textMuted))
                }
            } else if (trips.isNotEmpty()) {
                Text("Found ${trips.size} ride${if (trips.size != 1) "s" else ""}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
                trips.forEach { trip -> CabShareTripCard(trip = trip) }
            }
        }
    }

    if (showPendingModal) {
        AlertDialog(
            onDismissRequest = { showPendingModal = false },
            title = { Text("Request Pending", fontWeight = FontWeight.Bold) },
            text = { Text("The host will see your request and respond soon. Check status in My Trips.", color = colors.textSecondary) },
            confirmButton = { AmazeButton("Got it", onClick = { showPendingModal = false }) },
            containerColor = colors.surface
        )
    }
}

@Composable
fun CabShareTripCard(trip: CabShareTrip) {
    val colors = AmazeTheme.colors
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinResult by remember { mutableStateOf<String?>(null) }

    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Column {
                        Text(trip.name.ifBlank { trip.reg_number }, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Hosted by ${trip.owner_name.ifBlank { trip.reg_number }}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                    }
                }
            }

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

            val fromHub = trip.from_hub_name
            Text(buildString {
                if (fromHub.isNotBlank()) append("$fromHub → ")
                append(trip.hub_name.ifBlank { "Hub #${trip.hub_id}" })
            }, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
                    Text("${trip.preferred_time} (±${trip.tolerance_hours}h)", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
                    Text(trip.travel_date, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                }
            }

            if (trip.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Text(trip.notes, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            }

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

            val jr = joinResult
            if (jr != null) {
                Text(jr, style = AmazeTheme.typography.smallLabel.copy(color = if (jr.contains("sent", ignoreCase = true) || jr.contains("locally", ignoreCase = true)) colors.success else colors.danger, fontWeight = FontWeight.Bold))
            } else {
                AmazeButton(
                    text = "Request to Join",
                    onClick = { showJoinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Send
                )
            }
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Trip", fontWeight = FontWeight.Bold) },
            text = { Text("Send a join request to ${trip.owner_name.ifBlank { trip.reg_number }}?", color = colors.textSecondary) },
            confirmButton = {
                AmazeButton("Send Request", onClick = {
                    joinResult = "Request sent!"
                    showJoinDialog = false
                    AppState.cabRequestJoinNew(trip.trip_id) { success, msg ->
                        joinResult = if (success) "Request sent!" else msg
                    }
                })
            },
            dismissButton = {
                Text(Strings.cancel, style = AmazeTheme.typography.body.copy(color = colors.textSecondary), modifier = Modifier.clickable { showJoinDialog = false })
            },
            containerColor = colors.surface
        )
    }
}

@Composable
fun CreateTripTab() {
    val colors = AmazeTheme.colors
    val hubs by AppState.cabHubs.collectAsState()
    val cabLoading by AppState.cabLoading.collectAsState()

    var fromHubId by remember { mutableStateOf("") }
    var toHubId by remember { mutableStateOf("") }
    var tripDate by remember { mutableStateOf("") }
    var tripTime by remember { mutableStateOf("") }
    var tolerance by remember { mutableStateOf("1.0") }
    var seats by remember { mutableStateOf("2") }
    var gender by remember { mutableStateOf("mixed") }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(hubs) {
        if (hubs.size >= 2) {
            if (fromHubId.isEmpty()) fromHubId = hubs[0].hub_id.toString()
            if (toHubId.isEmpty()) toHubId = hubs[1].hub_id.toString()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Publish a Ride", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Text("Add your route so others can request to share the cab.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))

        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("From", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
                        SelectHubField(value = fromHubId, onValueChange = { v ->
                            if (v == toHubId && hubs.size > 1) toHubId = hubs.first { it.hub_id.toString() != v }.hub_id.toString()
                            fromHubId = v
                        }, hubs = hubs, placeholder = "Select")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("To", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
                        SelectHubField(value = toHubId, onValueChange = { v ->
                            if (v == fromHubId && hubs.size > 1) fromHubId = hubs.first { it.hub_id.toString() != v }.hub_id.toString()
                            toHubId = v
                        }, hubs = hubs, placeholder = "Select")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazeTextField(value = tripDate, onValueChange = { tripDate = it }, label = "Date", placeholder = "2026-07-20", modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) })
                    AmazeTextField(value = tripTime, onValueChange = { tripTime = it }, label = "Time", placeholder = "14:00", modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Available Seats", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
                        SelectField(value = seats, onValueChange = { seats = it }, options = (1..5).map { it.toString() }, placeholder = "Seats")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tolerance", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
                        SelectField(value = tolerance, onValueChange = { tolerance = it }, options = listOf("0.5", "1.0", "1.5", "2.0"),
                            displayMap = mapOf("0.5" to "± 30 min", "1.0" to "± 1 hr", "1.5" to "± 1.5 hrs", "2.0" to "± 2 hrs"),
                            placeholder = "Tolerance")
                    }
                }

                Column {
                    Text("Gender Preference", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("mixed" to "Mixed", "boys" to "Boys", "girls" to "Girls").forEach { (value, label) ->
                            val selected = gender == value
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.small))
                                    .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.surface)
                                    .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(AmazeTheme.radius.small))
                                    .clickable { gender = value }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = if (selected) colors.accent else colors.textSecondary))
                            }
                        }
                    }
                }

                AmazeTextField(value = notes, onValueChange = { notes = it }, label = "Notes (optional)", placeholder = "e.g. Bringing luggage")
            }
        }

        message?.let { (msg, isSuccess) ->
            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
            Text(msg, style = AmazeTheme.typography.body.copy(color = if (isSuccess) colors.success else colors.danger, fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = Modifier.height(AmazeTheme.spacing.md))
        AmazeButton(text = if (cabLoading) "Publishing..." else "Post Ride", onClick = {
            message = null
            if (fromHubId.isBlank() || toHubId.isBlank() || tripDate.isBlank() || tripTime.isBlank()) {
                message = Pair("Fill all required fields", false)
                return@AmazeButton
            }
            if (fromHubId == toHubId) {
                message = Pair("From and To cannot be the same.", false)
                return@AmazeButton
            }
            AppState.cabCreateTripNew(
                fromHubId = fromHubId.toIntOrNull() ?: return@AmazeButton,
                toHubId = toHubId.toIntOrNull() ?: return@AmazeButton,
                date = tripDate, time = tripTime,
                tolerance = tolerance.toDoubleOrNull() ?: 1.0,
                seats = seats.toIntOrNull() ?: 2,
                gender = gender, notes = notes
            ) { success, msg ->
                message = Pair(msg, success)
                if (success) {
                    tripDate = ""; tripTime = ""; notes = ""
                }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !cabLoading, icon = Icons.Rounded.DirectionsCar)
    }
}

@Composable
fun MyTripsTab() {
    val colors = AmazeTheme.colors
    var myTrips by remember { mutableStateOf<List<CabShareTrip>>(emptyList()) }
    var joinedTrips by remember { mutableStateOf<List<CabShareTrip>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        AppState.cabRefreshMyTripsNew { my, joined ->
            myTrips = my; joinedTrips = joined; loading = false
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
            Text(Strings.loading, style = AmazeTheme.typography.body.copy(color = colors.textMuted))
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        message?.let { (msg, isSuccess) ->
            Text(msg, style = AmazeTheme.typography.body.copy(color = if (isSuccess) colors.success else colors.danger, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
        }

        Text("Rides I Posted", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

        if (myTrips.isEmpty()) {
            Text("No posted rides.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary), modifier = Modifier.padding(vertical = 8.dp))
        } else {
            myTrips.forEach { trip -> MyPostedTripCard(trip = trip, onRefresh = { refreshTick++ }, onMessage = { msg, success -> message = Pair(msg, success) }) }
        }

        Spacer(modifier = Modifier.height(AmazeTheme.spacing.lg))
        Text("Rides I Requested", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

        if (joinedTrips.isEmpty()) {
            Text("No ride requests.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary), modifier = Modifier.padding(vertical = 8.dp))
        } else {
            joinedTrips.forEach { trip -> MyJoinedTripCard(trip = trip) }
        }
    }
}

@Composable
fun MyPostedTripCard(trip: CabShareTrip, onRefresh: () -> Unit, onMessage: (String, Boolean) -> Unit) {
    val colors = AmazeTheme.colors

    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(buildString {
                        if (trip.from_hub_name.isNotBlank()) append("${trip.from_hub_name} → ")
                        append(trip.hub_name.ifBlank { "Hub #${trip.hub_id}" })
                    }, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("${trip.travel_date} · ${trip.preferred_time} (±${trip.tolerance_hours}h)", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                }
                AmazeBadge(text = trip.status.uppercase(), variant = if (trip.status == "active") BadgeVariant.INFO else BadgeVariant.DANGER)
            }

            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
            Text("Join Requests", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textSecondary))

            if (trip.requests.isEmpty()) {
                Text("No requests yet.", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted), modifier = Modifier.padding(vertical = 4.dp))
            } else {
                trip.requests.forEach { req ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(req.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            if (req.status == "accepted") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Phone, contentDescription = null, tint = colors.success, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
                                    Text(req.phone_number, style = AmazeTheme.typography.smallLabel.copy(color = colors.success))
                                }
                            }
                        }
                        if (req.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.success.copy(alpha = 0.15f)).clickable {
                                    AppState.cabHandleMatchAction(req.match_id, "accept") { s, _ ->
                                        if (s) { onMessage("Request accepted.", true); onRefresh() }
                                    }
                                }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Accept", tint = colors.success, modifier = Modifier.size(18.dp))
                                }
                                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.danger.copy(alpha = 0.15f)).clickable {
                                    AppState.cabHandleMatchAction(req.match_id, "reject") { s, _ ->
                                        if (s) { onMessage("Request rejected.", true); onRefresh() }
                                    }
                                }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Reject", tint = colors.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            AmazeBadge(text = req.status.uppercase(), variant = if (req.status == "accepted") BadgeVariant.SUCCESS else BadgeVariant.DANGER)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyJoinedTripCard(trip: CabShareTrip) {
    val colors = AmazeTheme.colors

    AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(buildString {
                    if (trip.from_hub_name.isNotBlank()) append("${trip.from_hub_name} → ")
                    append(trip.hub_name.ifBlank { "Hub #${trip.hub_id}" })
                }, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("${trip.travel_date} · ${trip.preferred_time}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(AmazeTheme.spacing.xs))
                Text("Host: ${trip.owner_name.ifBlank { trip.reg_number }}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                if (trip.match_status == "accepted" && trip.owner_phone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
                        Text(trip.owner_phone, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.success))
                    }
                }
            }
            AmazeBadge(
                text = trip.match_status?.uppercase() ?: "PENDING",
                variant = when (trip.match_status) {
                    "accepted" -> BadgeVariant.SUCCESS
                    "rejected" -> BadgeVariant.DANGER
                    else -> BadgeVariant.WARNING
                }
            )
        }
    }
}

@Composable
fun SelectHubField(value: String, onValueChange: (String) -> Unit, hubs: List<CabShareHub>, placeholder: String) {
    val colors = AmazeTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.small)).padding(horizontal = 12.dp, vertical = 12.dp).clickable { if (hubs.isNotEmpty()) expanded = true }
    ) {
        if (hubs.isEmpty()) {
            Text("Loading hubs...", style = AmazeTheme.typography.body.copy(color = colors.textMuted))
        } else {
            val selected = hubs.find { it.hub_id.toString() == value }
            Text(selected?.hub_name ?: placeholder, style = AmazeTheme.typography.body.copy(fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal, color = if (selected != null) colors.textPrimary else colors.textMuted))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            hubs.forEach { hub ->
                DropdownMenuItem(
                    text = { Text(hub.hub_name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)) },
                    onClick = { onValueChange(hub.hub_id.toString()); expanded = false }
                )
            }
        }
    }
}

@Composable
fun SelectField(value: String, onValueChange: (String) -> Unit, options: List<String>, displayMap: Map<String, String> = emptyMap(), placeholder: String) {
    val colors = AmazeTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.small)).padding(horizontal = 12.dp, vertical = 12.dp).clickable { expanded = true }) {
        Text(displayMap[value] ?: value.ifBlank { placeholder }, style = AmazeTheme.typography.body.copy(fontWeight = if (value.isNotBlank()) FontWeight.Bold else FontWeight.Normal, color = if (value.isNotBlank()) colors.textPrimary else colors.textMuted))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(displayMap[opt] ?: opt, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)) },
                    onClick = { onValueChange(opt); expanded = false }
                )
            }
        }
    }
}
