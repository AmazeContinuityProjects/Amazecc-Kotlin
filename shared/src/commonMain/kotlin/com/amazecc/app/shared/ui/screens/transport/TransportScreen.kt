package com.amazecc.app.shared.ui.screens.transport

import androidx.compose.foundation.background
import com.amazecc.app.shared.ui.components.ScreenHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun TransportScreen() {
    val colors = AmazeTheme.colors
    val transportRes by AppState.transport.collectAsState()
    val buses = transportRes?.buses ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "Transport",
            description = "Dayboarder status and bus routes",
            showBackButton = false,
            showSyncButton = true
        )

        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("DAYBOARDER STATUS", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transportRes?.dayBoarderStatus ?: "APPROVED (Bus Pass Active)",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Bus Timings & Routes", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(12.dp))

        if (buses.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No bus routes found.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(buses) { bus ->
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.accent)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Route No: ${bus.routeNo}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Text(bus.routeName, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text("Departs at: ${bus.time}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                if (bus.driverName != null) {
                                    Text("Driver: ${bus.driverName} (${bus.driverPhone})", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
