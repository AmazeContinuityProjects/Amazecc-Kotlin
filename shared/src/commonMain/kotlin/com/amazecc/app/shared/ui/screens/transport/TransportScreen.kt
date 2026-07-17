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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun TransportScreen() {
    val colors = AmazeTheme.colors
    val transportRoutesRes by AppState.transportRoutes.collectAsState()
    val transportPassRes by AppState.transportPass.collectAsState()
    val routes = transportRoutesRes?.routes ?: emptyList()
    val passInfo = transportPassRes
    
    var searchQuery by remember { mutableStateOf("") }
    var expandedRouteNo by remember { mutableStateOf<String?>(null) }
    var showRegistrationForm by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val filteredRoutes = remember(routes, searchQuery) {
        if (searchQuery.isBlank()) routes
        else routes.filter {
            it.routeNo.contains(searchQuery, ignoreCase = true) ||
            it.routeName.contains(searchQuery, ignoreCase = true) ||
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
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            item {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(colors.accent, colors.accent.copy(alpha = 0.7f), colors.accent.copy(alpha = 0.0f))
                            )
                        )
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
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
                                "Dayscholar Bus Hub",
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
                                "Search and explore bus routes"
                            }
                            Text(
                                subtitle,
                                style = AmazeTheme.typography.caption.copy(color = Color.White.copy(alpha = 0.8f))
                            )
                        }
                    }
                }
            }
            
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TransportRegistrationCard(
                        passInfo = passInfo,
                        colors = colors,
                        onApplyClick = { showRegistrationForm = true },
                        onHistoryClick = { showHistoryDialog = true }
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
                items(filteredRoutes, key = { it.routeNo }) { route ->
                    val isExpanded = expandedRouteNo == route.routeNo
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
    }

    if (showRegistrationForm) {
        RegistrationDialog(
            routes = routes,
            colors = colors,
            onDismiss = { showRegistrationForm = false }
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            registrations = passInfo?.registrations ?: emptyList(),
            colors = colors,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@Composable
private fun TransportRegistrationCard(
    passInfo: TransportPassRes?,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onApplyClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val status = passInfo?.status ?: "inactive"
    val isActive = status == "active"
    val isPending = status == "pending"

    val passState = when {
        isActive -> "active"
        isPending -> "pending"
        else -> "inactive"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                when (passState) {
                    "active" -> Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981)))
                    "pending" -> Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B)))
                    else -> Brush.linearGradient(listOf(colors.surface, colors.background))
                }
            )
            .border(
                1.dp,
                when (passState) {
                    "active" -> Color(0xFF10B981).copy(alpha = 0.3f)
                    "pending" -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                    else -> colors.border
                },
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        if (passState == "inactive") {
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
                            .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.BusAlert, null, tint = Color(0xFFD97706), modifier = Modifier.size(24.dp))
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
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (passState == "active") Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Transport Pass",
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (passState == "active") "Active" else "Pending Approval",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (passInfo?.registrations?.isNotEmpty() == true) {
                        IconButton(onClick = onHistoryClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.History, "History", tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            "ROUTE",
                            style = AmazeTheme.typography.smallLabel.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                        passInfo?.routeName?.let {
                            Text(it, style = AmazeTheme.typography.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                        }
                        passInfo?.routeNo?.let {
                            Text("Route $it", style = AmazeTheme.typography.caption.copy(color = Color.White.copy(alpha = 0.9f)))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "VALID UNTIL",
                            style = AmazeTheme.typography.smallLabel.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                        passInfo?.validUntil?.let {
                            Text(it, style = AmazeTheme.typography.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationDialog(
    routes: List<BusRouteDetail>,
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
                
                if (resultMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isError) Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF10B981).copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Text(
                            resultMessage!!,
                            color = if (isError) Color(0xFFDC2626) else Color(0xFF059669),
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
                                delay(1500)
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
private fun HistoryDialog(
    registrations: List<TransportRegItem>,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
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
                        "Registration History",
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = colors.textMuted)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(registrations, key = { it.id }) { reg ->
                        RegistrationHistoryCard(reg = reg, colors = colors)
                    }
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
            .background(colors.background)
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
private fun BusRouteCard(
    route: BusRouteDetail,
    isExpanded: Boolean,
    onClick: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val isAC = route.busType?.contains("AC", ignoreCase = true) == true
    val gradientColors = if (isAC) {
        listOf(Color(0xFF3B82F6).copy(alpha = 0.15f), Color(0xFF60A5FA).copy(alpha = 0.05f))
    } else {
        listOf(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF34D399).copy(alpha = 0.05f))
    }
    val themeColor = if (isAC) Color(0xFF3B82F6) else Color(0xFF10B981)

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
                            "#${route.routeNo}",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = themeColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            route.routeName,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            if (!route.busType.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(themeColor.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        route.busType,
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
                                "${route.stops.size} stops",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        route.departureTime,
                        style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                    Text(
                        "Departure",
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
