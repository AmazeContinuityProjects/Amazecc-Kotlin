package com.amazecc.app.shared.ui.screens

import androidx.compose.foundation.background
import com.amazecc.app.shared.ui.components.ScreenHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.getScreenIconAndLabel

@Composable
fun MoreScreen() {
    val colors = AmazeTheme.colors
    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    var isEditing by remember { mutableStateOf(false) }

    val allModules = listOf(
        Screen.ATTENDANCE, Screen.ACADEMICS, Screen.LIBRARIES, Screen.HOSTEL,
        Screen.CABSHARE, Screen.TRANSPORT, Screen.PAYMENTS, Screen.PROFILE,
        Screen.EVENTS, Screen.QBANK, Screen.SOCIAL, Screen.FFCS_PLANNER, Screen.FREE_CLASSROOMS
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = if (isEditing) "Edit Navigation" else "App Library",
            description = if (isEditing) "Select up to 4 modules to pin to your bottom bar" else "All available modules and services",
            showBackButton = false,
            showSyncButton = false
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            AmazeButton(
                text = if (isEditing) "Done" else "Customize Navigation",
                onClick = { isEditing = !isEditing }
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(allModules) { module ->
                val (icon, label) = getScreenIconAndLabel(module)
                val isPinned = pinnedTabs.contains(module)

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isEditing && isPinned) colors.accent.copy(alpha = 0.2f)
                            else colors.surface
                        )
                        .clickable {
                            if (isEditing) {
                                if (isPinned) {
                                    AppState.setPinnedNavTabs(pinnedTabs - module)
                                } else if (pinnedTabs.size < 4) {
                                    AppState.setPinnedNavTabs(pinnedTabs + module)
                                }
                            } else {
                                AppState.navigateTo(module)
                            }
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isEditing && isPinned) colors.accent else colors.textPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = label,
                            style = AmazeTheme.typography.smallLabel.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isEditing && isPinned) colors.accent else colors.textSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}
