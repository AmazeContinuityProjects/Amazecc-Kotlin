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
            // Horizontal scrollable tabs
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
    
    Column(modifier = Modifier.fillMaxSize()) {
        AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search routes or stops...", style = AmazeTheme.typography.body.copy(color = colors.textMuted))
            }
        }
        
        if (buses.isEmpty()) {
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
                items(buses) { bus ->
                    AmazeCard {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.DirectionsBus, contentDescription = null, tint = colors.accent)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(bus.routeNo, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                }
                                AmazeBadge("Active", variant = BadgeVariant.SUCCESS)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Route: ${bus.routeName}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                AmazeButton("View Stops", onClick = {}, variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                AmazeButton("Track", onClick = {}, variant = ButtonVariant.PRIMARY, modifier = Modifier.weight(1f))
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
    Column(modifier = Modifier.fillMaxWidth()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Box(
                    modifier = Modifier.size(60.dp).background(colors.accent.copy(alpha = 0.1f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Transport Pass", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("No active transport pass found for your account. Register for the upcoming semester via VTOP.", 
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                AmazeButton("Check VTOP portal", onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}