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
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*
import com.amazecc.app.shared.ui.strings.Strings

@Composable
fun AboutScreen() {
    val colors = AmazeTheme.colors
    var currentVersion by remember { mutableStateOf("...") }
    LaunchedEffect(Unit) {
        currentVersion = com.amazecc.app.shared.utils.UpdateConfig.getCurrentVersion()
    }
    var showChangelog by remember { mutableStateOf(false) }
    val changes = changelogEntries

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = Strings.about, description = "AmazeCC Student Companion", showBackButton = true)

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
