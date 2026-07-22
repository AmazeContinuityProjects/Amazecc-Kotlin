@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun ProfileScreen() {
    val colors = AmazeTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = "Profile",
            description = "Your personal information",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshProfile
        )

        Box(modifier = Modifier.weight(1f)) {
            ProfileContent(colors)
        }
    }
}

@Composable
private fun ProfileContent(colors: com.amazecc.app.shared.theme.AmazeColors) {
    val profile by AppState.studentProfile.collectAsState()
    val authorizedID by SessionManager.authorizedID.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp).padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Avatar & name card
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.15f), colors.accent.copy(alpha = 0.05f)))
                ).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(colors.accent, colors.accent.copy(alpha = 0.7f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (authorizedID ?: "?").take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile?.name ?: authorizedID ?: "Student",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = profile?.regNo ?: authorizedID ?: "",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(colors.chart1.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text("ACTIVE", color = colors.chart1, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }

        // Profile info cards
        if (profile != null) {
            ProfileGroupCard("Personal Info", listOf(
                ProfileItem(Icons.Rounded.Email, "Email", profile!!.email),
                ProfileItem(Icons.Rounded.Phone, "Mobile", profile!!.mobile),
                ProfileItem(Icons.Rounded.School, "Program", profile!!.program),
                ProfileItem(Icons.Rounded.LocationOn, "Campus", profile!!.campus),
                ProfileItem(Icons.Rounded.DateRange, "Batch", profile!!.batch),
            ), colors)

            val extraItems = mutableListOf<ProfileItem>()
            profile!!.section?.let { extraItems.add(ProfileItem(Icons.Rounded.Group, "Section", it)) }
            profile!!.advisorName?.let { extraItems.add(ProfileItem(Icons.Rounded.Person, "Advisor", it)) }
            profile!!.bloodGroup?.let { extraItems.add(ProfileItem(Icons.Rounded.Bloodtype, "Blood Group", it)) }

            if (extraItems.isNotEmpty()) {
                ProfileGroupCard("Additional Info", extraItems, colors)
            }
        } else {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Icon(Icons.Rounded.PersonSearch, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Profile not loaded", color = colors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    AmazeButton("Load Profile", onClick = { AppState.loadAllData() }, variant = ButtonVariant.SECONDARY)
                }
            }
        }

        // Settings button
        AmazeCard(modifier = Modifier.fillMaxWidth().clickable(onClick = { AppState.navigateTo(Screen.SETTINGS) })) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Settings, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("App Settings", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
                    Text("Theme, sync, credentials & more", color = colors.textSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private data class ProfileItem(val icon: ImageVector, val label: String, val value: String)

@Composable
private fun ProfileGroupCard(title: String, items: List<ProfileItem>, colors: com.amazecc.app.shared.theme.AmazeColors) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, color = colors.textMuted, fontSize = 10.sp)
                        Text(item.value, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}



