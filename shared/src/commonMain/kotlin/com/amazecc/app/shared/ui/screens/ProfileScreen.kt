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
import com.amazecc.app.shared.model.StudentProfile
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*

@Composable
fun ProfileScreen() {
    val colors = AmazeTheme.colors
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        ScreenHeader(
            title = if (showSettings) "Settings" else "Profile",
            description = if (showSettings) "App preferences & sync" else "Your personal information",
            showBackButton = false,
            showSyncButton = true,
            onRefresh = AppState::refreshProfile
        )

        Box(modifier = Modifier.weight(1f)) {
            if (showSettings) SettingsContent(colors) { showSettings = false }
            else ProfileContent(colors) { showSettings = true }
        }
    }
}

@Composable
private fun ProfileContent(colors: com.amazecc.app.shared.theme.AmazeColors, onSettingsClick: () -> Unit) {
    val profile by AppState.studentProfile.collectAsState()
    val authorizedID by SessionManager.authorizedID.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
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
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text("ACTIVE", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
        AmazeCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onSettingsClick)) {
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

// ── SETTINGS ──

@Composable
private fun SettingsContent(colors: com.amazecc.app.shared.theme.AmazeColors, onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance section
        SettingsSection("Appearance", Icons.Rounded.Palette, colors) {
            val activeTheme by AppState.theme.collectAsState()
            val activeAccent by AppState.accent.collectAsState()

            SettingsRow("Theme", colors) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazeButton("Light", { AppState.changeTheme(AppTheme.LIGHT) }, modifier = Modifier.weight(1f), variant = if (activeTheme == AppTheme.LIGHT) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                    AmazeButton("Dark", { AppState.changeTheme(AppTheme.DARK) }, modifier = Modifier.weight(1f), variant = if (activeTheme == AppTheme.DARK) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                }
            }

            SettingsRow("Accent Color", colors) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccentSwatch("Ocean", AccentTheme.OCEAN, activeAccent, colors, Modifier.weight(1f))
                    AccentSwatch("Forest", AccentTheme.FOREST, activeAccent, colors, Modifier.weight(1f))
                    AccentSwatch("Lavender", AccentTheme.LAVENDER, activeAccent, colors, Modifier.weight(1f))
                    AccentSwatch("Sunset", AccentTheme.SUNSET, activeAccent, colors, Modifier.weight(1f))
                }
            }
        }

        // Display section
        SettingsSection("Display", Icons.Rounded.Visibility, colors) {
            val cgpaHidden by AppState.cgpaHidden.collectAsState()
            val attendanceMode by AppState.attendanceDisplayMode.collectAsState()

            SettingsToggle("Hide CGPA", cgpaHidden, { AppState.setCgpaHidden(it) }, colors)
            SettingsRow("Attendance Display", colors) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmazeButton("Percentage", { AppState.setAttendanceDisplayMode("percentage") }, modifier = Modifier.weight(1f), variant = if (attendanceMode == "percentage") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                    AmazeButton("Fraction", { AppState.setAttendanceDisplayMode("fraction") }, modifier = Modifier.weight(1f), variant = if (attendanceMode == "fraction") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                }
            }
        }

        // Sync section
        SettingsSection("Data Sync", Icons.Rounded.Sync, colors) {
            val syncArrear by AppState.syncArrear.collectAsState()
            val syncExam by AppState.syncExam.collectAsState()
            val syncProfile by AppState.syncProfile.collectAsState()
            val syncAdditional by AppState.syncAdditional.collectAsState()

            SettingsToggle("Arrear Data", syncArrear, { AppState.setSyncArrear(it) }, colors)
            SettingsToggle("Exam Schedule", syncExam, { AppState.setSyncExam(it) }, colors)
            SettingsToggle("Profile Data", syncProfile, { AppState.setSyncProfile(it) }, colors)
            SettingsToggle("Additional (Projects/Wishlist)", syncAdditional, { AppState.setSyncAdditional(it) }, colors)
        }

        // Credentials section
        SettingsSection("Credentials", Icons.Rounded.Lock, colors) {
            val savedUsername = SettingsManager.getString(SettingsManager.KEY_USERNAME)
            val savedPassword = SettingsManager.getString(SettingsManager.KEY_PASSWORD)

            SettingsRow("Saved Credentials", colors) {
                Text(if (savedUsername.isNotBlank()) "$savedUsername / ****" else "No credentials saved", color = colors.textSecondary, fontSize = 12.sp)
            }

            var showCredEditor by remember { mutableStateOf(false) }
            if (showCredEditor) {
                var username by remember { mutableStateOf(savedUsername) }
                var password by remember { mutableStateOf("") }
                AmazeTextField(value = username, onValueChange = { username = it }, label = "Registration Number", placeholder = "", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AmazeTextField(value = password, onValueChange = { password = it }, label = "Password", placeholder = "", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AmazeButton("Save & Overwrite", onClick = { SettingsManager.saveCredentials(username, password) }, modifier = Modifier.fillMaxWidth())
            }
            AmazeButton(if (showCredEditor) "Cancel" else "Edit Credentials", onClick = { showCredEditor = !showCredEditor }, variant = ButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
        }

        // Danger zone
        SettingsSection("Danger Zone", Icons.Rounded.Warning, colors) {
            AmazeButton("Clear All Caches", onClick = { SettingsManager.clearAll() }, variant = ButtonVariant.DANGER, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            AmazeButton("Close Student Session", onClick = { AppState.logout() }, variant = ButtonVariant.DANGER, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, colors: com.amazecc.app.shared.theme.AmazeColors, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsRow(label: String, colors: com.amazecc.app.shared.theme.AmazeColors, content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.accent, checkedTrackColor = colors.accent.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun AccentSwatch(name: String, accent: AccentTheme, current: AccentTheme, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    val selected = accent == current
    val swatchColor = when (accent) {
        AccentTheme.OCEAN -> Color(0xFF3B82F6)
        AccentTheme.FOREST -> Color(0xFF10B981)
        AccentTheme.LAVENDER -> Color(0xFF8B5CF6)
        AccentTheme.SUNSET -> Color(0xFFF59E0B)
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.surface)
            .border(if (selected) 1.dp else 0.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { AppState.changeAccent(accent) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(swatchColor))
            Spacer(Modifier.height(4.dp))
            Text(name, color = if (selected) colors.accent else colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
        }
    }
}
