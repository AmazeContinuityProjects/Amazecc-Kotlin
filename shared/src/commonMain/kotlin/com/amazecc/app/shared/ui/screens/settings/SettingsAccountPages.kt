package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.MoodleRes
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.utils.UpdateConfig

@Composable
fun CredentialsPage() {
    val colors = AmazeTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupLabel("Saved Accounts")
        SettingsGroupCard {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                val savedUsername = SettingsManager.getString(SettingsManager.KEY_USERNAME)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("VTOP Credentials", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                        Text(if (savedUsername.isNotBlank()) savedUsername else "No credentials saved", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                    }
                    AmazeBadge(
                        text = if (savedUsername.isNotBlank()) "CONNECTED" else "NOT SAVED",
                        variant = if (savedUsername.isNotBlank()) BadgeVariant.SUCCESS else BadgeVariant.WARNING
                    )
                }

                var showCredEditor by remember { mutableStateOf(false) }
                if (showCredEditor) {
                    var username by remember { mutableStateOf(savedUsername) }
                    var password by remember { mutableStateOf("") }
                    Spacer(Modifier.height(4.dp))
                    AmazeTextField(value = username, onValueChange = { username = it }, label = "Registration Number", placeholder = "e.g. 21BCE0001", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    AmazeTextField(value = password, onValueChange = { password = it }, label = "Password", placeholder = "••••••••", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmazeButton("Cancel", onClick = { showCredEditor = false }, variant = ButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                        AmazeButton("Save & Overwrite", onClick = { SettingsManager.saveCredentials(username, password); showCredEditor = false }, modifier = Modifier.weight(1f))
                    }
                } else {
                    AmazeButton("Edit VTOP Credentials", onClick = { showCredEditor = true }, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        SettingsGroupLabel("Linked Portals")
        SettingsGroupCard {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                val moodleCreds = SettingsManager.getMoodleCredentials()
                val libCreds = SettingsManager.getLibraryCredentials()

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Moodle LMS Account", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                        Text(if (moodleCreds != null) "Linked as ${moodleCreds.first}" else "Not linked", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                    }
                    if (moodleCreds != null) {
                        TextButton(onClick = { SettingsManager.clearMoodleCredentials(); AppState.updateMoodleData(MoodleRes(success = false, message = "Cleared")) }) {
                            Text("Unlink", color = colors.dangerText, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { AppState.navigateTo(Screen.MOODLE) }) {
                            Text("Link", color = colors.accent, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                SettingsRowDivider()

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Library Account", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.base)
                        Text(if (libCreds != null) "Linked as ${libCreds.first}" else "Not linked", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
                    }
                    if (libCreds != null) {
                        TextButton(onClick = { SettingsManager.clearLibraryCredentials(); AppState.saveLibraryCredentials("", "") }) {
                            Text("Unlink", color = colors.dangerText, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { AppState.navigateTo(Screen.LIBRARIES) }) {
                            Text("Link", color = colors.accent, fontSize = AmazeTheme.fontSize.sm, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutPage() {
    val colors = AmazeTheme.colors
    var currentVersion by remember { mutableStateOf("...") }
    LaunchedEffect(Unit) {
        currentVersion = UpdateConfig.getCurrentVersion()
    }
    val updateStatus by AppState.updateStatus.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.16f))
                        .border(2.dp, colors.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Insights, null, tint = colors.accent, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                Text("AmazeCC", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.height(4.dp))
                AmazeBadge(text = "v$currentVersion", variant = BadgeVariant.INFO)
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                Text(
                    "Your all-in-one student companion for VIT — built with Kotlin Multiplatform & Compose.",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        SettingsGroupCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazeButton(
                        text = "Check for Updates",
                        onClick = { AppState.forceCheckForUpdate() },
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.weight(1f),
                        enabled = updateStatus !is AppState.UpdateStatus.Checking
                    )
                    when (updateStatus) {
                        is AppState.UpdateStatus.Checking -> {
                            Text("Checking…", style = AmazeTheme.typography.body.copy(color = colors.accent, fontWeight = FontWeight.Medium), modifier = Modifier.align(Alignment.CenterVertically).padding(16.dp))
                        }
                        is AppState.UpdateStatus.UpToDate -> {
                            Text("Up to date ✓", style = AmazeTheme.typography.body.copy(color = colors.success, fontWeight = FontWeight.Medium), modifier = Modifier.align(Alignment.CenterVertically).padding(16.dp))
                        }
                        is AppState.UpdateStatus.Error -> {
                            Text("Check failed", style = AmazeTheme.typography.body.copy(color = colors.danger, fontWeight = FontWeight.Medium), modifier = Modifier.align(Alignment.CenterVertically).padding(16.dp))
                        }
                        else -> {}
                    }
                }
            }
        }

        SettingsGroupLabel("Resources")
        SettingsGroupCard {
            SettingsRow(
                icon = Icons.Rounded.Info,
                title = "About & Resources",
                subtitle = "Version info, open source & legal",
                tint = colors.accent,
                onClick = { AppState.navigateTo(Screen.ABOUT) }
            )
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Rounded.Star,
                title = "What's New",
                subtitle = "Release notes & recent changes",
                tint = colors.accent,
                onClick = { AppState.navigateTo(Screen.CHANGELOG) }
            )
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Rounded.EmojiEvents,
                title = "Hall of Fame",
                subtitle = "Top contributors & achievers",
                tint = colors.accent,
                onClick = { AppState.navigateTo(Screen.HALL_OF_FAME) }
            )
        }
    }
}

@Composable
fun DangerPage(
    onClearCache: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = AmazeTheme.colors

    SettingsGroupCard(danger = true) {
        SettingsRow(
            icon = Icons.Rounded.DeleteSweep,
            title = "Clear All Local Caches",
            subtitle = "Remove cached data from device storage",
            tint = colors.danger,
            danger = true,
            onClick = onClearCache
        )
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Rounded.Logout,
            title = "Log Out Student Session",
            subtitle = "Sign out and return to the login screen",
            tint = colors.danger,
            danger = true,
            onClick = onLogout
        )
    }
}