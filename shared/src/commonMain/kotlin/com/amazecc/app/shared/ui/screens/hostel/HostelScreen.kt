package com.amazecc.app.shared.ui.screens.hostel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun HostelScreen() {
    val colors = AmazeTheme.colors
    val hostelDetails by AppState.hostelDetails.collectAsState()
    val hostelLeaves by AppState.hostelLeaves.collectAsState()

    var activeSubTab by remember { mutableStateOf("Details") }
    val tabs = listOf("Details", "Mess Menu", "Laundry", "Counselling")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Hostel Hub",
            description = "Manage mess, outings, laundry & counseling",
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (activeSubTab) {
                    "Details" -> HostelDetailsTab(hostelDetails, hostelLeaves?.leaves ?: emptyList())
                    "Mess Menu" -> HostelMessTab()
                    "Laundry" -> HostelLaundryTab()
                    "Counselling" -> HostelCounsellingTab()
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun HostelDetailsTab(hostelDetails: com.amazecc.app.shared.model.HostelDetails?, leaves: List<com.amazecc.app.shared.model.LeaveItem>) {
    val colors = AmazeTheme.colors
    
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("HOSTEL BOOKING DETAILS", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Block / Room", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text(
                        if (hostelDetails?.blockName.isNullOrEmpty()) "N/A" 
                        else "${hostelDetails?.blockName} / ${hostelDetails?.roomNo}", 
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Gender", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text(if (hostelDetails?.gender.isNullOrEmpty()) "N/A" else hostelDetails?.gender!!, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Mess Facility", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Text(if (hostelDetails?.messInfo.isNullOrEmpty()) "Not Enrolled" else hostelDetails?.messInfo!!, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text("Outing & Leave History", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))

    if (leaves.isEmpty()) {
        Text("No leaves applied.", color = colors.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
    } else {
        leaves.forEach { leave ->
            AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(leave.leaveType ?: "Leave", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        AmazeBadge(
                            text = leave.status ?: "PENDING",
                            variant = if (leave.status == "APPROVED" || leave.status == "COMPLETED") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Destination: ${leave.visitPlace ?: "Ã¢â‚¬â€"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text("Reason: ${leave.reason ?: "Ã¢â‚¬â€"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Period: ${leave.from} to ${leave.to}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                }
            }
        }
    }
}

@Composable
fun HostelMessTab() {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Mess Menu Integration", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Text("Select your mess type to view today's interactive menu.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            AmazeButton("View Special Menu", onClick = {}, variant = ButtonVariant.SECONDARY)
        }
    }
}

@Composable
fun HostelLaundryTab() {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Rounded.LocalLaundryService, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Laundry Schedule", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Text("Check block-wise laundry slots powered by Unmessify data.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmazeButton("Boys Block", onClick = {}, variant = ButtonVariant.SECONDARY)
                AmazeButton("Girls Block", onClick = {}, variant = ButtonVariant.SECONDARY)
            }
        }
    }
}

@Composable
fun HostelCounsellingTab() {
    val colors = AmazeTheme.colors
    Text("Faculty Advisor", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
    Spacer(modifier = Modifier.height(12.dp))
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(colors.accent.copy(alpha = 0.1f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Your Faculty Advisor", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Assigned via VTOP", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            }
            Icon(Icons.Rounded.Email, contentDescription = "Email", tint = colors.textMuted)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    AmazeCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.accent.copy(alpha = 0.05f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HeadsetMic, contentDescription = null, tint = colors.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Campus Counselling", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("For academic stress, personal counseling, or mental health support, please visit the campus health center.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
            Spacer(modifier = Modifier.height(12.dp))
            AmazeButton("Contact Counsellor", onClick = {}, variant = ButtonVariant.PRIMARY, modifier = Modifier.fillMaxWidth())
        }
    }
}