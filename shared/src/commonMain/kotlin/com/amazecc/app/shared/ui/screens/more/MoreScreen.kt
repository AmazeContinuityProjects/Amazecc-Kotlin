package com.amazecc.app.shared.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun MoreScreen() {
    val colors = AmazeTheme.colors

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = "More",
            description = "Modules, Communities and Settings",
            showBackButton = false,
            showSyncButton = false
        )
        
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            
            Text("App Library", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(12.dp))
            
            val modules = listOf(
                Pair(Screen.PAYMENTS, Icons.Rounded.CreditCard to "Payments"),
                Pair(Screen.LIBRARIES, Icons.Rounded.LibraryBooks to "Library"),
                Pair(Screen.HOSTEL, Icons.Rounded.Apartment to "Hostel"),
                Pair(Screen.TRANSPORT, Icons.Rounded.DirectionsBus to "Transport"),
                Pair(Screen.CABSHARE, Icons.Rounded.DirectionsCar to "Cabshare"),
                Pair(Screen.EVENTS, Icons.Rounded.Event to "Events"),
                Pair(Screen.QBANK, Icons.Rounded.Topic to "QBank"),
                Pair(Screen.SOCIAL, Icons.Rounded.People to "Social"),
                Pair(Screen.FFCS_PLANNER, Icons.Rounded.ViewTimeline to "FFCS"),
                Pair(Screen.FREE_CLASSROOMS, Icons.Rounded.MeetingRoom to "Classes")
            )
            
            // Render as a grid (3 columns)
            val chunkedModules = modules.chunked(3)
            AmazeCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    chunkedModules.forEach { rowModules ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowModules.forEach { (screen, iconAndLabel) ->
                                val (icon, label) = iconAndLabel
                                ModuleIcon(
                                    icon = icon,
                                    label = label,
                                    onClick = { AppState.navigateTo(screen) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill empty slots if row has less than 3 items
                            repeat(3 - rowModules.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Communities", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(12.dp))
            
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Club Hub", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Explore student clubs and chapters", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Community Feed", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Latest posts from AmazeCC members", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Settings & Info", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(12.dp))
            
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow("App Settings", Icons.Rounded.Settings)
                    SettingsRow("About AmazeCC", Icons.Rounded.Info)
                    Spacer(modifier = Modifier.height(12.dp))
                    AmazeButton("Log Out", onClick = {}, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun ModuleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label, 
            style = AmazeTheme.typography.smallLabel.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingsRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = AmazeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().clickable {}.padding(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
    }
}