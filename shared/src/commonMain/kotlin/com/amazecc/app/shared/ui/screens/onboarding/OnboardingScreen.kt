package com.amazecc.app.shared.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeTextField

@Composable
fun OnboardingScreen() {
    val colors = AmazeTheme.colors
    
    var residentialStatus by remember { mutableStateOf(AppState.residentialStatus.value) }
    
    // The modules the user can select
    val availableModules = listOf(
        Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.HOSTEL,
        Screen.CABSHARE, Screen.TRANSPORT, Screen.PAYMENTS, Screen.PROFILE,
        Screen.EVENTS, Screen.QBANK, Screen.SOCIAL
    )
    
    var selectedModules by remember { mutableStateOf(AppState.pinnedNavTabs.value) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "Welcome to AmazeCC!",
                style = AmazeTheme.typography.heading.copy(fontWeight = FontWeight.Black, fontSize = 28.sp, color = colors.textPrimary),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Let's set up your experience.",
                style = AmazeTheme.typography.body.copy(color = colors.textSecondary),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // removed friendlyName section

        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Residential Status", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("hosteller", "dayscholar", "unknown").forEach { status ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (residentialStatus == status) colors.accent else colors.surface)
                                    .clickable { residentialStatus = status }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = status.replaceFirstChar { it.uppercase() },
                                    color = if (residentialStatus == status) colors.background else colors.textPrimary,
                                    style = AmazeTheme.typography.smallLabel
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                "Pin your favorite modules (Max 4)",
                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val chunkedModules = availableModules.chunked(2)
        chunkedModules.forEach { rowModules ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowModules.forEach { module ->
                        val isSelected = selectedModules.contains(module)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.surface)
                                .clickable {
                                    if (isSelected) {
                                        selectedModules = selectedModules - module
                                    } else if (selectedModules.size < 4) {
                                        selectedModules = selectedModules + module
                                    }
                                }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = module.name,
                                color = if (isSelected) colors.accent else colors.textSecondary,
                                style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    if (rowModules.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            AmazeButton(
                text = "Get Started",
                onClick = {
                    
                    AppState.setResidentialStatus(residentialStatus)
                    AppState.setPinnedNavTabs(selectedModules)
                    AppState.navigateTo(Screen.HOME)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
