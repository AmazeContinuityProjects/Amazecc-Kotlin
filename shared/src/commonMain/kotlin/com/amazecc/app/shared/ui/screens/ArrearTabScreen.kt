package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.screens.m3e.M3EStateBridge

@Composable
fun ArrearTabScreen() {
    val isDark = M3EStateBridge.isDarkThemeActive()
    AmazeM3ETheme(isDark = isDark) {
        val colors = AmazeTheme.colors
        var activeSubTab by remember { mutableStateOf("Schedule") }
        val tabs = listOf("Schedule", "Details", "Grades")

        AppBackHandler {
            if (!AppState.navigateBack()) AppState.navigateBackTo(Screen.ACADEMICS)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {

            Column(modifier = Modifier.fillMaxSize()) {
                com.amazecc.app.shared.ui.components.HeaderSpacer()
                
                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { tab ->
                        val isSelected = activeSubTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(if (isSelected) colors.accent else colors.surface)
                                .clickable { activeSubTab = tab }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = tab,
                                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold).copy(
                                    color = if (isSelected) colors.onAccent else colors.textSecondary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = BOTTOM_NAV_PADDING)
                ) {
                    when (activeSubTab) {
                        "Schedule" -> ArrearScheduleTab()
                        "Details" -> ArrearDetailsTab()
                        "Grades" -> ArrearGradesTab()
                    }
                }
            }
        }
    }
}

@Composable
fun ArrearScheduleTab() {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text("No arrear exams scheduled", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
fun ArrearDetailsTab() {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text("No current arrear details", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
fun ArrearGradesTab() {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text("No past arrear grades found", style = AmazeTheme.typography.body.copy(color = colors.textSecondary, fontWeight = FontWeight.Medium))
        }
    }
}
