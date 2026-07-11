package com.amazecc.app.shared.ui.screens

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
fun ProfileScreen() {
    val colors = AmazeTheme.colors
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val activeTheme by AppState.theme.collectAsState()
    val activeAccent by AppState.accent.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "App Preferences",
            description = "Themes, accents, and session logout",
            showBackButton = false,
            showSyncButton = false
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("VIT University student", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                        Text(authorizedID ?: "DEMO123", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Text("Session state: ACTIVE", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.success))
                    }
                }
            }

            Column {
                Text("Select App Theme", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmazeButton(
                        text = "Light",
                        onClick = { AppState.changeTheme(AppTheme.LIGHT) },
                        variant = if (activeTheme == AppTheme.LIGHT) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Dark",
                        onClick = { AppState.changeTheme(AppTheme.DARK) },
                        variant = if (activeTheme == AppTheme.DARK) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Midnight",
                        onClick = { AppState.changeTheme(AppTheme.MIDNIGHT) },
                        variant = if (activeTheme == AppTheme.MIDNIGHT) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column {
                Text("Select Accent Palette", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmazeButton(
                        text = "Ocean",
                        onClick = { AppState.changeAccent(AccentTheme.OCEAN) },
                        variant = if (activeAccent == AccentTheme.OCEAN) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Forest",
                        onClick = { AppState.changeAccent(AccentTheme.FOREST) },
                        variant = if (activeAccent == AccentTheme.FOREST) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AmazeButton(
                        text = "Lavender",
                        onClick = { AppState.changeAccent(AccentTheme.LAVENDER) },
                        variant = if (activeAccent == AccentTheme.LAVENDER) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                AmazeButton(
                    text = "Sunset (Orange)",
                    onClick = { AppState.changeAccent(AccentTheme.SUNSET) },
                    variant = if (activeAccent == AccentTheme.SUNSET) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                val uiScale by AppState.uiScale.collectAsState()
                Text("Global UI Scale: ${(uiScale * 100).toInt()}%", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Slider(
                    value = uiScale,
                    onValueChange = { AppState.changeUiScale(it) },
                    valueRange = 0.5f..1.5f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.accent.copy(alpha = 0.2f)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Display Settings", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                
                val decimalValues by AppState.decimalValues.collectAsState()
                val friendlyName by AppState.friendlyName.collectAsState()
                val calendarView by AppState.calendarView.collectAsState()
                val residentialStatus by AppState.residentialStatus.collectAsState()

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Decimal Values", style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                        Text("Show exact decimal percentages", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    Switch(checked = decimalValues, onCheckedChange = { AppState.setDecimalValues(it) })
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Friendly Names", style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                        Text("Use short names for subjects", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                    Switch(checked = friendlyName, onCheckedChange = { AppState.setFriendlyName(it) })
                }
                
                AmazeDropdown(
                    label = "Calendar View Format",
                    selectedOption = calendarView,
                    options = listOf("List", "Grid"),
                    onOptionSelected = { AppState.setCalendarView(it) }
                )
                
                AmazeDropdown(
                    label = "Residential Status",
                    selectedOption = residentialStatus,
                    options = listOf("Hosteller", "Day Boarder"),
                    onOptionSelected = { AppState.setResidentialStatus(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(12.dp))

            AmazeButton(
                text = "Close Student Session",
                onClick = { AppState.logout() },
                variant = ButtonVariant.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

