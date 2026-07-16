package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.getScreenIconAndLabel

@Composable
fun SettingsScreen() {
    val colors = AmazeTheme.colors
    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    
    // Maintain a local mutable copy of selections for immediate UI feedback
    var selectedTabs by remember { mutableStateOf(pinnedTabs.toSet()) }
    
    // Pool of available tabs to pick from
    val availableTabs = listOf(
        Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.PROFILE,
        Screen.PAYMENTS, Screen.CABSHARE, Screen.TRANSPORT, Screen.CALENDAR,
        Screen.FFCS_PLANNER, Screen.FREE_CLASSROOMS, Screen.QBANK, Screen.SOCIAL,
        Screen.PROJECTS, Screen.WISHLIST
    )

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { AppState.navigateBack() }) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "App Settings",
                    style = AmazeTheme.typography.heading.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                Text(
                    text = "Customize your quick access navigation",
                    style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Glassmorphic Instruction Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.glassSurface)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = "Info", tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Select up to 4 tabs to display on the bottom navigation bar. (Home is always pinned).",
                    style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontSize = 14.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Available Screens (${selectedTabs.size}/4)",
            modifier = Modifier.padding(horizontal = 24.dp),
            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(availableTabs) { tab ->
                val (icon, label) = getScreenIconAndLabel(tab)
                val isSelected = selectedTabs.contains(tab)
                val isEnabled = isSelected || selectedTabs.size < 4

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colors.glassSurface else colors.surface)
                        .clickable(enabled = isEnabled || isSelected) {
                            val newSet = if (isSelected) {
                                selectedTabs - tab
                            } else {
                                if (selectedTabs.size < 4) selectedTabs + tab else selectedTabs
                            }
                            selectedTabs = newSet
                            AppState.setPinnedNavTabs(newSet.toList())
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) colors.accent else if (!isEnabled) colors.textMuted else colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = label,
                        style = AmazeTheme.typography.body.copy(
                            color = if (isSelected) colors.textPrimary else if (!isEnabled) colors.textMuted else colors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        enabled = isEnabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.accent,
                            uncheckedColor = colors.textMuted,
                            checkmarkColor = colors.background
                        )
                    )
                }
            }
            item { 
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Data Sync",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .clickable { AppState.loadAllData() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent.copy(alpha=0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Sync, contentDescription = "Sync", tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync All Data",
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        )
                        Text(
                            text = "Fetch the latest data from VTOP.",
                            style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}
