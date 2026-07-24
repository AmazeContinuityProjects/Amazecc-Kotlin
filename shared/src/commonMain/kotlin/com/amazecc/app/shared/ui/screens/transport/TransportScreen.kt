package com.amazecc.app.shared.ui.screens.transport

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.ScreenHeader
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TransportScreen() {
    val colors = AmazeTheme.colors
    val transportDataRes by AppState.transportData.collectAsState()
    val busesRes by AppState.buses.collectAsState()
    val routes = busesRes?.buses ?: emptyList()
    val transportData = transportDataRes
    
    var searchQuery by remember { mutableStateOf("") }
    var expandedRouteNo by remember { mutableStateOf<String?>(null) }
    var showRegistrationForm by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val filteredRoutes = remember(routes, searchQuery) {
        if (searchQuery.isBlank()) routes
        else routes.filter {
            it.route.contains(searchQuery, ignoreCase = true) ||
            it.id.contains(searchQuery, ignoreCase = true) ||
            it.stops.any { s -> s.stopName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        containerColor = colors.background,
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                ScreenHeader(
                    title = "Dayscholar Bus Hub",
                    description = if (transportData?.hasRegistration == true) {
                        "${transportData.busRouteId ?: ""} - Pass Active"
                    } else if (routes.isNotEmpty()) {
                        "${routes.size} routes available"
                    } else {
                        "Search and explore bus routes"
                    },
                    showSyncButton = true,
                    onRefresh = { AppState.refreshTransport() }
                )
                com.amazecc.app.shared.ui.components.HeaderSpacer()
            }
            
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TransportRegistrationCard(
                        transportData = transportData,
                        colors = colors,
                        onApplyClick = { showRegistrationForm = true }
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                items(filteredRoutes, key = { it.id }) { route ->
                    val isExpanded = expandedRouteNo == route.id
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        BusRouteCard(
                            route = route,
                            isExpanded = isExpanded,
                            onClick = { expandedRouteNo = if (isExpanded) null else route.id },
                            colors = colors
                        )
                    }
                }
            }
        }
    }

    if (showRegistrationForm) {
        RegistrationDialog(
            routes = routes,
            colors = colors,
            onDismiss = { showRegistrationForm = false }
        )
    }
}

@Composable
private fun TransportRegistrationCard(
    transportData: TransportDataRes?,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onApplyClick: () -> Unit
) {
    val isActive = transportData?.hasRegistration == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    if (isActive) listOf(colors.accent.copy(alpha = 0.15f), colors.accent.copy(alpha = 0.05f))
                    else listOf(colors.surface.copy(alpha = 0.8f), colors.background)
                )
            )
            .border(
                1.dp,
                if (isActive) colors.accent.copy(alpha = 0.3f) else colors.border,
                RoundedCornerShape(24.dp)
            )
    ) {
        // Background blob for glassmorphism effect
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = (-50).dp)
                    .background(colors.accent.copy(alpha = 0.2f), shape = CircleShape)
                    .blur(40.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (!isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colors.chart3.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.BusAlert, null, tint = colors.chart3, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "No Bus Registration",
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Text(
                                "Apply for a new pass to view it here.",
                                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onApplyClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.DirectionsBus,
                                    null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Dayscholar Transport",
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 20.sp)
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accent)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Registration Active",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                "ROUTE",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary)
                            )
                            transportData?.routeSelected?.let {
                                Text(it, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold))
                            }
                            transportData?.busRouteId?.let {
                                Text("Bus $it", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "REG. NO.",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary)
                            )
                            transportData?.registerNumber?.let {
                                Text(it, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { /* TODO: Implement tracking or open VTOP */ },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Track Bus", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationDialog(
    routes: List<BusRoute>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onDismiss: () -> Unit
) {
    var selectedRouteNo by remember { mutableStateOf("") }
    var selectedRouteDisplay by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf("") }
    var routeExpanded by remember { mutableStateOf(false) }
    var semesterExpanded by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf("") }
    var studentPhone by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val routeOptions = remember(routes) {
        routes.map { it.id to "${it.id} - ${it.route}" }
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

    val scope = rememberCoroutineScope()
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Apply for Pass",
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    IconButton(onClick = onDismiss, enabled = !submitting) {
                        Icon(Icons.Rounded.Close, null, tint = colors.textMuted)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                val rm = resultMessage
                if (rm != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isError) colors.chart5.copy(alpha = 0.1f) else colors.chart1.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Text(
                            rm,
                            color = if (isError) colors.chart5 else colors.chart1,
                            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = studentName,
                    onValueChange = { studentName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = studentPhone,
                    onValueChange = { studentPhone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box {
                    OutlinedTextField(
                        value = selectedRouteDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Route") },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, tint = colors.textMuted) },
                        modifier = Modifier.fillMaxWidth().clickable { routeExpanded = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border
                        )
                    )
                    DropdownMenu(
                        expanded = routeExpanded,
                        onDismissRequest = { routeExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
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
                Spacer(modifier = Modifier.height(12.dp))

                Box {
                    OutlinedTextField(
                        value = selectedSemester,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Semester") },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, tint = colors.textMuted) },
                        modifier = Modifier.fillMaxWidth().clickable { semesterExpanded = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border
                        )
                    )
                    DropdownMenu(
                        expanded = semesterExpanded,
                        onDismissRequest = { semesterExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
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
                                resultMessage = result.message ?: "Application submitted!"
                                isError = false
                                delay(1500.milliseconds)
                                onDismiss()
                            } else {
                                resultMessage = result.message ?: "Submission failed"
                                isError = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    enabled = studentName.isNotBlank() && studentPhone.isNotBlank() &&
                            selectedRouteNo.isNotBlank() && selectedSemester.isNotBlank() && !submitting
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit Application", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BusRouteCard(
    route: BusRoute,
    isExpanded: Boolean,
    onClick: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val isAC = route.type.contains("AC", ignoreCase = true) == true
    val gradientColors = if (isAC) {
        listOf(colors.chart2.copy(alpha = 0.15f), colors.chart2.copy(alpha = 0.05f))
    } else {
        listOf(colors.chart1.copy(alpha = 0.15f), colors.chart1.copy(alpha = 0.05f))
    }
    val themeColor = if (isAC) colors.chart2 else colors.chart1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradientColors))
            .background(colors.surface.copy(alpha = 0.85f))
            .border(1.dp, themeColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Blob effect
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 30.dp)
                .background(themeColor.copy(alpha = 0.15f), shape = CircleShape)
                .blur(40.dp)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(themeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "#${route.id}",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = themeColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            route.route,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            if (route.type.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(themeColor.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        route.type,
                                        color = themeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(Icons.Rounded.Map, null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${route.stops?.size ?: 0} stops",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        route.busLocation.ifBlank { "N/A" },
                        style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                    Text(
                        "Location",
                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 9.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
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
                                                else if (index == sortedStops.lastIndex) themeColor
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
                                    if (stop.pickupTime != null) {
                                        Text(
                                            stop.pickupTime,
                                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (route.driverName.isNotBlank() || route.driverInchargeName?.isNotBlank() == true || route.supervisorName?.isNotBlank() == true) {
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

                    var hasPreviousCrew = false

                    if (route.driverName.isNotBlank()) {
                        CrewRow(
                            icon = Icons.Rounded.Person,
                            label = "Driver",
                            name = route.driverName,
                            phone = route.driverPhone,
                            colors = colors
                        )
                        hasPreviousCrew = true
                    }

                    if (route.driverInchargeName?.isNotBlank() == true) {
                        if (hasPreviousCrew) Spacer(modifier = Modifier.height(8.dp))
                        CrewRow(
                            icon = Icons.Rounded.AssignmentInd,
                            label = "Driver Incharge",
                            name = route.driverInchargeName ?: return,
                            phone = route.driverInchargePhone,
                            colors = colors
                        )
                        hasPreviousCrew = true
                    }

                    if (route.supervisorName?.isNotBlank() == true) {
                        if (hasPreviousCrew) Spacer(modifier = Modifier.height(8.dp))
                        CrewRow(
                            icon = Icons.Rounded.SupervisorAccount,
                            label = "Supervisor",
                            name = route.supervisorName ?: return,
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
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = 10.sp)
            )
            Text(
                name,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            )
            if (!phone.isNullOrBlank()) {
                Text(
                    phone,
                    style = AmazeTheme.typography.caption.copy(color = colors.accent)
                )
            }
        }
        if (!phone.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.1f))
                    .clickable { uriHandler.openUri("tel:$phone") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Call, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}
