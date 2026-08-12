package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.utils.UpdateConfig

@Composable
fun AboutScreen() {
    val colors = AmazeTheme.colors
    var currentVersion by remember { mutableStateOf("...") }
    LaunchedEffect(Unit) {
        currentVersion = UpdateConfig.getCurrentVersion()
    }
    val updateStatus by AppState.updateStatus.collectAsState()
    val latestReleaseNotes by AppState.latestReleaseNotes.collectAsState()
    var showChangelog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateCheckMessage by remember { mutableStateOf("") }
    val changes = if (latestReleaseNotes.isNotBlank()) {
        latestReleaseNotes.lines().filter { it.isNotBlank() }.map { "• $it" }
    } else {
        changelogEntries
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
        ) {
            item { HeaderSpacer() }
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.16f))
                                .border(2.dp, colors.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Insights, null, tint = colors.accent, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.md))
                        Text("AmazeCC", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(4.dp))
                        AmazeBadge(text = "v$currentVersion", variant = BadgeVariant.INFO)
                        Spacer(Modifier.height(AmazeTheme.spacing.md))
                        Text(
                            "Your all-in-one student companion for VIT. Track attendance, manage academics, explore campus life, and stay connected.",
                            style = AmazeTheme.typography.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmazeButton(
                            text = "Check for Updates",
                            onClick = {
                                checkingUpdate = true
                                updateCheckMessage = ""
                                AppState.forceCheckForUpdate()
                            },
                            icon = Icons.Rounded.Download,
                            modifier = Modifier.weight(1f),
                            enabled = !checkingUpdate && updateStatus !is AppState.UpdateStatus.Checking
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
                            else -> {
                                if (updateCheckMessage.isNotBlank()) {
                                    Text(updateCheckMessage, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), modifier = Modifier.align(Alignment.CenterVertically).padding(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                AmazeButton(text = "What's New (Changelog)", onClick = { showChangelog = true }, icon = Icons.Rounded.Star, modifier = Modifier.fillMaxWidth())
            }

            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmazeSectionHeader(title = "App Information & Credits", icon = Icons.Rounded.Code)
                        Text("Developed by the AmazeCC Open Source Team", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
                        Text("Built with Kotlin Multiplatform & Compose Multiplatform", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                        Text("Powered by VTOP API & Secure Local Cache", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    }
                }
            }
        }
    }

    if (showChangelog) {
        ChangelogModal(version = currentVersion, changes = changes, onDismiss = { showChangelog = false })
    }
}