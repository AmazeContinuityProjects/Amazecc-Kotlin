package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun AboutScreen() {
    val colors = AmazeTheme.colors
    var showChangelog by remember { mutableStateOf(false) }
    val changes = listOf(
        "Phase 3: 15 new features including QBank, Faculty Info, Course Management, Projects, Wishlist, Feedback, Documents, Activity Tree, Spotlight Search, and more",
        "Phase 2: Hostel (Mess/Laundry/Counselling), Transport, CabShare, Events, Social modules",
        "Phase 1: Attendance Predictor, Arrear Management, Makeup & Compre, Circulars, Curriculum, OD Tracker, Marks Timeline, Vitol Wallet",
        "Phase 0: Foundation with Settings, Profile, Grades, GPA Predictor",
        "Initial release with Attendance, Timetable, Academic Calendar, Libraries, Payments"
    )

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = "About", description = "AmazeCC v2.0.0", showBackButton = true)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Insights, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("AmazeCC", style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 24.sp))
                        Text("Version 2.0.0", style = AmazeTheme.typography.body.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(8.dp))
                        Text("Your all-in-one student companion for VIT. Track attendance, manage academics, explore campus life, and stay connected.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp))
                    }
                }
            }

            item {
                AmazeButton(text = "What's New", onClick = { showChangelog = true }, icon = Icons.Rounded.Star, modifier = Modifier.fillMaxWidth())
            }

            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Credits", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(8.dp))
                        Text("Developed by the AmazeCC Team", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                        Text("Built with Kotlin Multiplatform & Compose", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                        Text("Powered by VTOP API", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    }
                }
            }
        }
    }

    if (showChangelog) {
        ChangelogModal(version = "2.0.0", changes = changes, onDismiss = { showChangelog = false })
    }
}
