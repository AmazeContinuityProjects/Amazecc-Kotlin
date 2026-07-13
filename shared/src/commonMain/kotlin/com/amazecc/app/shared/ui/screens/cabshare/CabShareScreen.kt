package com.amazecc.app.shared.ui.screens.cabshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Person
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
    Column(modifier = Modifier.fillMaxWidth()) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search by destination or date...", style = AmazeTheme.typography.body.copy(color = colors.textMuted))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        // Mock Ride
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = colors.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chennai Airport (MAA)", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    AmazeBadge("2 SEATS LEFT", variant = BadgeVariant.WARNING)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Date", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Text("Oct 25, 2:00 PM", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Host", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        Text("S. Rajan", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                AmazeButton("Request to Join", onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun CreateTripTab() {
    val colors = AmazeTheme.colors
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Host a Ride", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Text("Share your cab and split the fare.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        Spacer(modifier = Modifier.height(24.dp))
        AmazeButton("Create New Trip", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun MyTripsTab() {
    val colors = AmazeTheme.colors
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No active trips.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
        }
    }
}