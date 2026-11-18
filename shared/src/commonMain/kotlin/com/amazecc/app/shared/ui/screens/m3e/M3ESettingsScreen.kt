package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3ESettingsAction
import com.amazecc.app.shared.domain.overhaul.M3ESettingsState
import com.amazecc.app.shared.domain.overhaul.M3EThemeMode
import com.amazecc.app.shared.vtop.VtopSource
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive System & Settings Hub.
 * 100% Stateless: accepts [M3ESettingsState] and emits [M3ESettingsAction].
 * Features theme mode, M3E palette presets, Hono API connection, and offline cache purger.
 */
@Composable
fun M3ESettingsScreen(
    state: M3ESettingsState,
    onAction: (M3ESettingsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Text(
                        text = "Appearance, Engine & Diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = state.appVersion,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
            }
        }

        // 2. Appearance & Themes Card
        item {
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.LOW,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Theme & Display Mode",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }

                    M3EConnectedButtonGroup(
                        items = M3EThemeMode.entries,
                        selectedItem = state.selectedThemeMode,
                        onItemSelected = { onAction(M3ESettingsAction.SelectThemeMode(it)) },
                        labelProvider = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ColorLens,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "M3 Expressive Color Palette",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }

                    M3EConnectedButtonGroup(
                        items = M3EPalettePreset.entries,
                        selectedItem = state.selectedPalettePreset,
                        onItemSelected = { onAction(M3ESettingsAction.SelectPalettePreset(it)) },
                        labelProvider = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Hono API Backend Configuration
        item {
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.LOW,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Hono Backend API",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = colors.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.honoApiBaseUrl,
                        onValueChange = { onAction(M3ESettingsAction.UpdateHonoApiUrl(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Worker Base URL", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceContainerHigh,
                            unfocusedContainerColor = colors.surfaceContainerLow,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outlineVariant
                        )
                    )
                }
            }
        }

        // 4. Cache & Diagnostics Card
        item {
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.LOW,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Storage & Cache (${state.cacheSizeText})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ElevatedButton(
                            onClick = { onAction(M3ESettingsAction.ClearCache) },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.surfaceContainerHigh,
                                contentColor = colors.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Clear Cache",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        ElevatedButton(
                            onClick = { onAction(M3ESettingsAction.RunDiagnostics) },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer
                            )
                        ) {
                            if (state.isDiagnosticsRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = colors.onPrimaryContainer,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.HealthAndSafety,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Diagnostics",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (!state.diagnosticStatus.isNullOrBlank()) {
                        Text(
                            text = state.diagnosticStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary
                        )
                    }
                }
            }
        }

        // 4b. VTOP Sync Engine & Data Source Card
        item {
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.LOW,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "VTOP Engine & Data Source",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }

                    Text(
                        text = "Data Source Tier",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurfaceVariant
                    )

                    M3EConnectedButtonGroup(
                        items = listOf(VtopSource.REMOTE, VtopSource.LOCAL, VtopSource.OFFLINE_CACHE),
                        selectedItem = state.vtopSource,
                        onItemSelected = { onAction(M3ESettingsAction.SetVtopSource(it)) },
                        labelProvider = {
                            when (it) {
                                VtopSource.REMOTE -> "Tier 1: Remote"
                                VtopSource.LOCAL -> "Tier 2: Scraper"
                                VtopSource.OFFLINE_CACHE -> "Offline Cache"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Sync Mode Cadence",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurfaceVariant
                    )

                    val syncModes = listOf("quick", "balanced", "full", "academics", "campus")
                    M3EConnectedButtonGroup(
                        items = syncModes,
                        selectedItem = state.selectedSyncMode,
                        onItemSelected = { onAction(M3ESettingsAction.SelectSyncMode(it)) },
                        labelProvider = { it.replaceFirstChar { c -> c.uppercase() } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ElevatedButton(
                        onClick = { onAction(M3ESettingsAction.TriggerManualSync(state.selectedSyncMode)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Invoke Sync (${state.selectedSyncMode.replaceFirstChar { it.uppercase() }})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // 5. Notifications & Reminders Card
        item {
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.LOW,
                cornerRadius = 22.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Notifications & Reminders",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }

                    // Class Alerts Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Class Schedule Alerts",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = "Remind 10 mins before upcoming lecture",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.classAlertsEnabled,
                            onCheckedChange = { onAction(M3ESettingsAction.ToggleClassAlerts(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primary,
                                checkedTrackColor = colors.primaryContainer
                            )
                        )
                    }

                    // Attendance Alerts Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Low Attendance Warnings",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = "Alert when any course drops below 75%",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.attendanceAlertsEnabled,
                            onCheckedChange = { onAction(M3ESettingsAction.ToggleAttendanceAlerts(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primary,
                                checkedTrackColor = colors.primaryContainer
                            )
                        )
                    }

                    // Exam Reminders Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exam & Seating Notifications",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface
                            )
                            Text(
                                text = "Remind CAT / FAT schedule 24h prior",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.examRemindersEnabled,
                            onCheckedChange = { onAction(M3ESettingsAction.ToggleExamReminders(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primary,
                                checkedTrackColor = colors.primaryContainer
                            )
                        )
                    }

                    ElevatedButton(
                        onClick = { onAction(M3ESettingsAction.SendTestNotification) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Send Test Notification",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // 6. Open Source Credits Card
        item {
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.LOW,
                cornerRadius = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAction(M3ESettingsAction.OpenGithubRepo) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "AmazeCC Open Source",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface
                            )
                            Text(
                                text = "GitHub Repository & Documentation",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = "Open GitHub",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview
@Composable
fun M3ESettingsScreenLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ESettingsScreen(
            state = createMockSettingsState(),
            onAction = {}
        )
    }
}

@Preview
@Composable
fun M3ESettingsScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ESettingsScreen(
            state = createMockSettingsState(),
            onAction = {}
        )
    }
}

private fun createMockSettingsState() = M3ESettingsState(
    selectedThemeMode = M3EThemeMode.DARK_OLED,
    selectedPalettePreset = M3EPalettePreset.OCEAN_MIDNIGHT,
    honoApiBaseUrl = "https://amazecc-api-hono.sugeerth.workers.dev",
    cacheSizeText = "14.2 MB",
    isDiagnosticsRunning = false,
    diagnosticStatus = "All systems operational (Ping: 42ms)"
)
