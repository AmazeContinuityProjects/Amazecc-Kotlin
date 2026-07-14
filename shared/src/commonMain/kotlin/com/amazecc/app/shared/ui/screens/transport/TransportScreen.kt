package com.amazecc.app.shared.ui.screens.transport

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import kotlinx.coroutines.launch

@Composable
fun TransportScreen() {
    val colors = AmazeTheme.colors
    val transportRoutesRes by AppState.transportRoutes.collectAsState()
    val transportPassRes by AppState.transportPass.collectAsState()
    val routes = transportRoutesRes?.routes ?: emptyList()
    val passInfo = transportPassRes
    var activeTab by remember { mutableStateOf("Bus Routes") }
    val tabs = listOf("Bus Routes", "Registration", "My Pass")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.accent, colors.accent.copy(alpha = 0.7f), colors.accent.copy(alpha = 0.3f))
                    )
                )
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DirectionsBus, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            "Transport Hub",
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 22.sp
                            )
                        )
                        val subtitle = if (passInfo?.status == "active") {
                            "${passInfo.routeNo ?: ""} - ${passInfo.routeName ?: "Pass Active"}"
                        } else if (routes.isNotEmpty()) {
                            "${routes.size} routes available"
                        } else {
                            "Bus routes and registration"
                        }
                        Text(
                            subtitle,
                            style = AmazeTheme.typography.caption.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.White.copy(alpha = 0.4f)
                                    else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { activeTab = tab }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                tab,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                "Bus Routes" -> BusRoutesTab(routes)
                "Registration" -> RegistrationTab(routes)
                "My Pass" -> MyPassTab()
            }
        }
    }
}

@Composable
private fun BusRoutesTab(routes: List<BusRouteDetail>) {
    val colors = AmazeTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var expandedRouteNo by remember { mutableStateOf<String?>(null) }

    val filteredRoutes = remember(routes, searchQuery) {
        if (searchQuery.isBlank()) routes
        else routes.filter {
            it.routeNo.contains(searchQuery, ignoreCase = true) ||
            it.routeName.contains(searchQuery, ignoreCase = true) ||
            it.stops.any { s -> s.stopName.contains(searchQuery, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 30.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search routes or stops...", color = colors.textMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accent
                )
            )
        }

        if (filteredRoutes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.DirectionsBus, null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No routes found", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
                        Text("Try a different search term", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                    }
                }
            }
        } else {
            items(filteredRoutes, key = { it.routeNo }) { route ->
                val isExpanded = expandedRouteNo == route.routeNo
                BusRouteCard(
                    route = route,
                    isExpanded = isExpanded,
                    onClick = { expandedRouteNo = if (isExpanded) null else route.routeNo },
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun BusRouteCard(
    route: BusRouteDetail,
    isExpanded: Boolean,
    onClick: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DirectionsBus, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                route.routeNo,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            if (!route.busType.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.accent.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        route.busType,
                                        color = colors.accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            route.routeName,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                            maxLines = 1
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            route.departureTime,
                            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                        )
                        Text(
                            "Departure",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 9.sp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Active", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (route.stops.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "${route.stops.size} stops",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary)
                    )
                }
                if (!route.fare.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        route.fare,
                        style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.border)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (route.stops.isNotEmpty()) {
                    Text(
                        "Route Stops",
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val sortedStops = route.stops.sortedBy { it.stopOrder }
                    sortedStops.forEachIndexed { index, stop ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (index == 0) colors.accent
                                                else if (index == sortedStops.lastIndex) Color(0xFF10B981)
                                                else colors.border
                                            )
                                    )
                                    if (index < sortedStops.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(24.dp)
                                                .background(colors.border.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        stop.stopName,
                                        style = AmazeTheme.typography.body.copy(
                                            fontWeight = if (index == 0 || index == sortedStops.lastIndex) FontWeight.Bold else FontWeight.Medium,
                                            color = colors.textPrimary
                                        )
                                    )
                                    Text(
                                        stop.pickupTime,
                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                    )
                                }
                            }
                            if (!stop.fare.isNullOrBlank()) {
                                Text(
                                    stop.fare,
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                                )
                            }
                        }
                    }
                }

                if (route.driverName != null || route.supervisorName != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.border)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "Crew Details",
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    route.driverName?.let {
                        CrewRow(
                            icon = Icons.Rounded.Person,
                            label = "Driver",
                            name = it,
                            phone = route.driverPhone,
                            colors = colors
                        )
                    }
                    if (route.driverName != null && route.supervisorName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    route.supervisorName?.let {
                        CrewRow(
                            icon = Icons.Rounded.SupervisorAccount,
                            label = "Supervisor",
                            name = it,
                            phone = route.supervisorPhone,
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    name: String,
    phone: String?,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                label,
                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp)
            )
            Text(
                name,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            )
            if (phone != null) {
                Text(
                    phone,
                    style = AmazeTheme.typography.caption.copy(color = colors.accent)
                )
            }
        }
    }
}

@Composable
private fun RegistrationTab(routes: List<BusRouteDetail>) {
    val colors = AmazeTheme.colors
    val transportPassRes by AppState.transportPass.collectAsState()
    val registrations = transportPassRes?.registrations ?: emptyList()
    var selectedRouteNo by remember { mutableStateOf("") }
    var selectedRouteDisplay by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf("") }
    var routeExpanded by remember { mutableStateOf(false) }
    var semesterExpanded by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf("") }
    var studentPhone by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf(false) }

    val routeOptions = remember(routes) {
        routes.map { it.routeNo to "${it.routeNo} - ${it.routeName}" }
    }
    val semesters = remember {
        listOf(
            "Winter 2024-25",
            "Summer 2025",
            "Fall 2025",
            "Winter 2025-26",
            "Spring 2026",
            "Summer 2026"
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(submitResult) {
        submitResult?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            submitResult = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 30.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.accent.copy(alpha = 0.06f))
                        .border(1.dp, colors.accent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Apply for Transport Pass",
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Fill in your details and select a route and semester to apply for a new bus pass.",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            "Personal Details",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = studentName,
                            onValueChange = { studentName = it },
                            label = { Text("Full Name") },
                            placeholder = { Text("Enter your name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                cursorColor = colors.accent
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = studentPhone,
                            onValueChange = { studentPhone = it },
                            label = { Text("Phone Number") },
                            placeholder = { Text("Enter your phone number") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border,
                                cursorColor = colors.accent
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Route & Semester",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Box {
                            OutlinedTextField(
                                value = selectedRouteDisplay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Route") },
                                placeholder = { Text("Choose a route") },
                                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, tint = colors.textMuted) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { routeExpanded = true },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.border,
                                    cursorColor = colors.accent
                                )
                            )
                            DropdownMenu(
                                expanded = routeExpanded,
                                onDismissRequest = { routeExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                if (routeOptions.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No routes available", color = colors.textMuted) },
                                        onClick = { routeExpanded = false }
                                    )
                                } else {
                                    routeOptions.forEach { (no, display) ->
                                        DropdownMenuItem(
                                            text = { Text(display, color = colors.textPrimary) },
                                            onClick = {
                                                selectedRouteNo = no
                                                selectedRouteDisplay = display
                                                routeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box {
                            OutlinedTextField(
                                value = selectedSemester,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Semester") },
                                placeholder = { Text("Choose semester") },
                                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, tint = colors.textMuted) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { semesterExpanded = true },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.border,
                                    cursorColor = colors.accent
                                )
                            )
                            DropdownMenu(
                                expanded = semesterExpanded,
                                onDismissRequest = { semesterExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
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

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    submitting = true
                                    val result = AmazeClient.submitTransportRegistration(
                                        TransportRegRequest(
                                            routeNo = selectedRouteNo,
                                            semester = selectedSemester,
                                            studentName = studentName,
                                            studentPhone = studentPhone
                                        )
                                    )
                                    submitting = false
                                    if (result.success) {
                                        submitResult = result.message ?: "Application submitted!"
                                        submitError = false
                                        studentName = ""
                                        studentPhone = ""
                                        selectedRouteNo = ""
                                        selectedRouteDisplay = ""
                                        selectedSemester = ""
                                    } else {
                                        submitResult = result.message ?: "Submission failed"
                                        submitError = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                disabledContainerColor = colors.border
                            ),
                            enabled = studentName.isNotBlank() && studentPhone.isNotBlank() &&
                                    selectedRouteNo.isNotBlank() && selectedSemester.isNotBlank() && !submitting
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Send, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit Application", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (registrations.isNotEmpty()) {
                item {
                    Text(
                        "Registration History",
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }

                items(registrations, key = { it.id }) { reg ->
                    RegistrationHistoryCard(reg = reg, colors = colors)
                }
            }
        }
    }
}

@Composable
private fun RegistrationHistoryCard(
    reg: TransportRegItem,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val statusColor = when {
        reg.status.contains("Approved", ignoreCase = true) || reg.status.contains("Active", ignoreCase = true) -> Color(0xFF10B981)
        reg.status.contains("Pending", ignoreCase = true) -> Color(0xFFF59E0B)
        reg.status.contains("Expired", ignoreCase = true) || reg.status.contains("Rejected", ignoreCase = true) -> Color(0xFFEF4444)
        else -> colors.textMuted
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        reg.semester,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    )
                    Text(
                        "${reg.routeNo} - ${reg.routeName}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                        maxLines = 1
                    )
                    if (!reg.appliedOn.isNullOrBlank()) {
                        Text(
                            "Applied: ${reg.appliedOn}",
                            style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    reg.status,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MyPassTab() {
    val colors = AmazeTheme.colors
    val transportPassRes by AppState.transportPass.collectAsState()
    val passInfo = transportPassRes
    val status = passInfo?.status ?: "inactive"
    val isActive = status == "active"
    val isPending = status == "pending"

    val passState = when {
        isActive -> "active"
        isPending -> "pending"
        else -> "inactive"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when (passState) {
                        "active" -> Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981)))
                        "pending" -> Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B)))
                        else -> Brush.linearGradient(listOf(colors.border, colors.surface))
                    }
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (passState) {
                            "active" -> Icons.Rounded.CheckCircle
                            "pending" -> Icons.Rounded.Schedule
                            else -> Icons.Rounded.CreditCard
                        },
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Transport Pass",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 22.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        when (passState) {
                            "active" -> "Active"
                            "pending" -> "Pending Approval"
                            else -> "No Active Pass"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                if (isActive) {
                    Spacer(modifier = Modifier.height(16.dp))
                    passInfo?.routeName?.let {
                        Text(
                            it,
                            style = AmazeTheme.typography.body.copy(color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
                        )
                    }
                    passInfo?.routeNo?.let {
                        Text(
                            "Route $it",
                            style = AmazeTheme.typography.caption.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                    }
                    passInfo?.validUntil?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Valid until: $it",
                            style = AmazeTheme.typography.caption.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
                if (passState == "inactive") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No active transport pass found.",
                        style = AmazeTheme.typography.caption.copy(color = Color.White.copy(alpha = 0.7f))
                    )
                }
            }
        }

        passInfo?.dayBoarderStatus?.let { statusText ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DirectionsBus, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            "Day Boarder Status",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            statusText,
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                }
            }
        }

        if (!isActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent.copy(alpha = 0.06f))
                    .border(1.dp, colors.accent.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Go to the Registration tab to apply for a new pass.",
                        style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        val registrations = passInfo?.registrations ?: emptyList()
        if (registrations.isNotEmpty()) {
            Text(
                "Pass History",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )

            registrations.forEach { reg ->
                RegistrationHistoryCard(reg = reg, colors = colors)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
