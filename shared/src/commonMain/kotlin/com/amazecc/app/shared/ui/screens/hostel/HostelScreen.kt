package com.amazecc.app.shared.ui.screens.hostel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch


@Composable
fun HostelScreen() {
    val colors = AmazeTheme.colors
    val hostelDetails by AppState.hostelDetails.collectAsState()
    val hostelLeaves by AppState.hostelLeaves.collectAsState()

    var activeSubTab by remember { mutableStateOf("Details & Leave") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Hostel Portal",
            description = "Manage mess, outings & late requests",
            showBackButton = false,
            showSyncButton = true
        )

        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AmazeButton(
                    text = "Details & Leaves",
                    onClick = { activeSubTab = "Details & Leave" },
                    variant = if (activeSubTab == "Details & Leave") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AmazeButton(
                    text = "Late Hour Request",
                    onClick = { activeSubTab = "Late Hour" },
                    variant = if (activeSubTab == "Late Hour") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeSubTab == "Details & Leave") {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("HOSTEL BOOKING DETAILS", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Block / Room", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("${hostelDetails?.blockName ?: "Q-Block"} / ${hostelDetails?.roomNo ?: "612"}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Gender", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text(hostelDetails?.gender ?: "MALE", style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Mess Facility", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            Text(hostelDetails?.messInfo ?: "Veg Mess (Caterer: CRCL)", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Outing & Leave History", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(12.dp))

                    val leaves = hostelLeaves?.leaves ?: emptyList()
                    if (leaves.isEmpty()) {
                        Text("No leaves applied.", color = colors.textSecondary)
                    } else {
                        leaves.forEach { leave ->
                            AmazeCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(leave.leaveType ?: "Leave", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        AmazeBadge(
                                            text = leave.status ?: "PENDING",
                                            variant = if (leave.status == "APPROVED" || leave.status == "COMPLETED") BadgeVariant.SUCCESS else BadgeVariant.WARNING
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Destination: ${leave.visitPlace ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Reason: ${leave.reason ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Period: ${leave.from} to ${leave.to}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.textMuted))
                                }
                            }
                        }
                    }
                }
            } else {
                var reason by remember { mutableStateOf("") }
                var isSubmitted by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Late Hour Extension Request", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Submit request to extend entry timings back into hostel block (beyond 08:30 PM). Needs proctor approval.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    
                    AmazeTextField(
                        value = reason,
                        onValueChange = { reason = it; isSubmitted = false },
                        label = "Reason for Late Hour",
                        placeholder = "e.g., Working on Capstone Project in Lab"
                    )

                    if (isSubmitted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.successSurface, shape = MaterialTheme.shapes.small)
                                .padding(12.dp)
                        ) {
                            Text("Late Hour request submitted successfully!", color = colors.successText, fontWeight = FontWeight.Bold)
                        }
                    }

                    AmazeButton(
                        text = "Request Late Hour",
                        onClick = {
                            if (reason.isNotBlank()) {
                                isSubmitted = true
                                reason = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── 4. SUB-SERVICES SCREENS ──


