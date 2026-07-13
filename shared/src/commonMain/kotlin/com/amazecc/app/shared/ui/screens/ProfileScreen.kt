@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.ui.screens

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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var activeSubTab by remember { mutableStateOf("Info") }
    val tabs = listOf("Info", "Preferences", "Credentials", "Developer Cache")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Profile & Settings",
            description = "App preferences and local storage control",
            showBackButton = false,
            showSyncButton = false
        )

        TabRow(
            selectedTabIndex = tabs.indexOf(activeSubTab),
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(activeSubTab)]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = { Text(tab, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)) },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            when (activeSubTab) {
                "Info" -> ProfileInfoSubScreen()
                "Preferences" -> PreferencesSubScreen()
                "Credentials" -> CredentialsSubScreen()
                "Developer Cache" -> CacheInspectorSubScreen()
            }
        }
    }
}

@Composable
fun ProfileInfoSubScreen() {
    val colors = AmazeTheme.colors
    val profile by AppState.studentProfile.collectAsState()
    val authorizedID by SessionManager.authorizedID.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (authorizedID ?: "?").take(2).uppercase(),
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = profile?.name ?: authorizedID ?: "DEMO123",
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
                    Text(
                        text = profile?.regNo ?: authorizedID ?: "DEMO123",
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.success.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.success,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        if (profile != null) {
            ProfileInfoRow("Email", profile!!.email, Icons.Rounded.Email)
            ProfileInfoRow("Mobile", profile!!.mobile, Icons.Rounded.Phone)
            ProfileInfoRow("Program", profile!!.program, Icons.Rounded.School)
            ProfileInfoRow("Campus", profile!!.campus, Icons.Rounded.LocationOn)
            ProfileInfoRow("Batch", profile!!.batch, Icons.Rounded.DateRange)
            if (profile!!.section != null) ProfileInfoRow("Section", profile!!.section!!, Icons.Rounded.Group)
            if (profile!!.advisorName != null) ProfileInfoRow("Advisor", profile!!.advisorName!!, Icons.Rounded.Person)
            if (profile!!.bloodGroup != null) ProfileInfoRow("Blood Group", profile!!.bloodGroup!!, Icons.Rounded.Bloodtype)
        } else {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Profile data not loaded yet.", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                    Spacer(modifier = Modifier.height(8.dp))
                    AmazeButton("Load Profile", onClick = {
                        AppState.loadAllData()
                    }, variant = ButtonVariant.SECONDARY)
                }
            }
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                Text(value, style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
fun PreferencesSubScreen() {
    val colors = AmazeTheme.colors
    val activeTheme by AppState.theme.collectAsState()
    val activeAccent by AppState.accent.collectAsState()
    val cgpaHidden by AppState.cgpaHidden.collectAsState()
    val attendanceMode by AppState.attendanceDisplayMode.collectAsState()
    val syncArrear by AppState.syncArrear.collectAsState()
    val syncExam by AppState.syncExam.collectAsState()
    val syncProfile by AppState.syncProfile.collectAsState()
    val syncAdditional by AppState.syncAdditional.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("VIT University student", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                    val authorizedID = SessionManager.authorizedID.collectAsState().value ?: "DEMO123"
                    Text(authorizedID, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Text("Session state: ACTIVE", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.success))
                }
            }
        }

        // Theme and Display
        Column {
            Text("Select App Theme", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmazeButton("Light", { AppState.changeTheme(AppTheme.LIGHT) }, modifier = Modifier.weight(1f), variant = if (activeTheme == AppTheme.LIGHT) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                AmazeButton("Dark", { AppState.changeTheme(AppTheme.DARK) }, modifier = Modifier.weight(1f), variant = if (activeTheme == AppTheme.DARK) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
            }
        }

        // Accent color
        Column {
            Text("Accent Color", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AccentChip("Ocean", AccentTheme.OCEAN, activeAccent, colors, Modifier.weight(1f))
                AccentChip("Forest", AccentTheme.FOREST, activeAccent, colors, Modifier.weight(1f))
                AccentChip("Lavender", AccentTheme.LAVENDER, activeAccent, colors, Modifier.weight(1f))
                AccentChip("Sunset", AccentTheme.SUNSET, activeAccent, colors, Modifier.weight(1f))
            }
        }

        // Display section
        Column {
            Text("Display", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(8.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow("Hide CGPA", cgpaHidden, { AppState.setCgpaHidden(it) }, colors)
            }

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Attendance Display Mode", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmazeButton("Percentage", { AppState.setAttendanceDisplayMode("percentage") }, modifier = Modifier.weight(1f), variant = if (attendanceMode == "percentage") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                        AmazeButton("Fraction", { AppState.setAttendanceDisplayMode("fraction") }, modifier = Modifier.weight(1f), variant = if (attendanceMode == "fraction") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY)
                    }
                }
            }
        }

        // Sync section
        Column {
            Text("Sync Settings", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(modifier = Modifier.height(8.dp))

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToggleRow("Sync Arrear Data", syncArrear, { AppState.setSyncArrear(it) }, colors)
                    Divider(color = colors.border, thickness = 0.5.dp)
                    ToggleRow("Sync Exam Schedule", syncExam, { AppState.setSyncExam(it) }, colors)
                    Divider(color = colors.border, thickness = 0.5.dp)
                    ToggleRow("Sync Profile Data", syncProfile, { AppState.setSyncProfile(it) }, colors)
                    Divider(color = colors.border, thickness = 0.5.dp)
                    ToggleRow("Sync Additional (Proj./Wishlist)", syncAdditional, { AppState.setSyncAdditional(it) }, colors)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        AmazeButton("Close Student Session", { AppState.logout() }, modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.DANGER)
    }
}

@Composable
private fun AccentChip(name: String, accent: AccentTheme, current: AccentTheme, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    val selected = accent == current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.surface)
            .border(if (selected) 1.dp else 0.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { AppState.changeAccent(accent) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(name, style = AmazeTheme.typography.smallLabel.copy(
            color = if (selected) colors.accent else colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        ))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accent,
                checkedTrackColor = colors.accent.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun CredentialsSubScreen() {
    val colors = AmazeTheme.colors
    var username by remember { mutableStateOf(SettingsManager.getString(SettingsManager.KEY_USERNAME)) }
    var password by remember { mutableStateOf(SettingsManager.getString(SettingsManager.KEY_PASSWORD)) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Stored Credentials", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("These credentials are used by the app's background API service to fetch live updates.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))

                AmazeTextField(value = username, onValueChange = { username = it }, label = "Registration Number", placeholder = "", modifier = Modifier.fillMaxWidth())
                AmazeTextField(value = password, onValueChange = { password = it }, label = "Password", placeholder = "", modifier = Modifier.fillMaxWidth())

                AmazeButton("Save & Overwrite", onClick = {
                    SettingsManager.saveCredentials(username, password)
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun CacheInspectorSubScreen() {
    val colors = AmazeTheme.colors

    val caches = listOf(
        "Grades Cache" to SettingsManager.CACHE_GRADES,
        "Marks Cache" to SettingsManager.CACHE_MARKS,
        "Attendance Cache" to SettingsManager.CACHE_ATTENDANCE
    )

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Local Storage Debugger", style = AmazeTheme.typography.heading.copy(fontSize = 18.sp, color = colors.textPrimary))
        Text("View and clear raw cached API responses mimicking localStorage.", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))

        caches.forEach { (label, key) ->
            val data = SettingsManager.getNullableString(key)
            val sizeBytes = data?.length ?: 0

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(label, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(if (sizeBytes > 0) "${sizeBytes / 1024} KB stored" else "Empty", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                        AmazeButton("Clear", onClick = { SettingsManager.remove(key) }, variant = ButtonVariant.DANGER)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        AmazeButton("Clear All Caches", onClick = { SettingsManager.clearAll() }, variant = ButtonVariant.DANGER, modifier = Modifier.fillMaxWidth())
    }
}
