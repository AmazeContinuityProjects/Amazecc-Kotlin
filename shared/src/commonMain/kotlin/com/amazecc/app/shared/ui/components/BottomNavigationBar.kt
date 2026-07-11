package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme

@Composable
fun BottomNavigationBar() {
    val currentScreen by AppState.currentScreen.collectAsState()
    val colors = AmazeTheme.colors

    if (currentScreen == Screen.LOGIN) return

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 24.dp) // Floating effect
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF0F0F12)) // Pitch black with hint of grey
            .border(
                1.dp, 
                androidx.compose.ui.graphics.Color(0xFF262626), 
                androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
            )
            .padding(vertical = 12.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Icons.Rounded.Home,
            label = "Home",
            isSelected = currentScreen == Screen.DASHBOARD,
            onClick = { AppState.navigateTo(Screen.DASHBOARD) }
        )
        BottomNavItem(
            icon = Icons.Rounded.EventAvailable,
            label = "Attendance",
            isSelected = currentScreen == Screen.ATTENDANCE,
            onClick = { AppState.navigateTo(Screen.ATTENDANCE) }
        )
        BottomNavItem(
            icon = Icons.Rounded.School,
            label = "Academics",
            isSelected = currentScreen == Screen.MARKS || currentScreen == Screen.TIMETABLE,
            onClick = { AppState.navigateTo(Screen.MARKS) }
        )
        BottomNavItem(
            icon = Icons.Rounded.Payment,
            label = "Payments",
            isSelected = currentScreen == Screen.PAYMENTS,
            onClick = { AppState.navigateTo(Screen.PAYMENTS) }
        )
        BottomNavItem(
            icon = Icons.Rounded.Person,
            label = "Profile",
            isSelected = currentScreen == Screen.PROFILE,
            onClick = { AppState.navigateTo(Screen.PROFILE) }
        )
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val color = if (isSelected) colors.accent else colors.textMuted
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(
                color = color,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}
