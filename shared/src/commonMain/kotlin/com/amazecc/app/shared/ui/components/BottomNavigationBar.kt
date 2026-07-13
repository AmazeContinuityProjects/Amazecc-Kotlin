package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme

@Composable
fun BottomNavigationBar() {
    val currentScreen by AppState.currentScreen.collectAsState()
    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    if (currentScreen == Screen.LOGIN || currentScreen == Screen.ONBOARDING) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF0F0F12))
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(32.dp))
            .padding(vertical = 12.dp, horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            icon = Icons.Rounded.Home,
            contentDesc = "Home",
            isSelected = currentScreen == Screen.HOME,
            onClick = { AppState.navigateTo(Screen.HOME) }
        )

        pinnedTabs.forEach { tab ->
            val (icon, desc) = getScreenIconAndLabel(tab)
            BottomNavItem(
                icon = icon,
                contentDesc = desc,
                isSelected = currentScreen == tab,
                onClick = { AppState.navigateTo(tab) }
            )
        }

        BottomNavItem(
            icon = Icons.Rounded.Apps,
            contentDesc = "More",
            isSelected = currentScreen == Screen.MORE,
            onClick = { AppState.navigateTo(Screen.MORE) }
        )
    }
}

fun getScreenIconAndLabel(screen: Screen): Pair<ImageVector, String> {
    return when (screen) {
        Screen.HOME -> Icons.Rounded.Home to "Home"
        Screen.ATTENDANCE -> Icons.Rounded.EventAvailable to "Attendance"
        Screen.ACADEMICS -> Icons.Rounded.School to "Academics"
        Screen.PAYMENTS -> Icons.Rounded.CreditCard to "Payments"
        Screen.LIBRARIES -> Icons.AutoMirrored.Rounded.LibraryBooks to "Library"
        Screen.HOSTEL -> Icons.Rounded.Apartment to "Hostel"
        Screen.CABSHARE -> Icons.Rounded.DirectionsCar to "Cabshare"
        Screen.TRANSPORT -> Icons.Rounded.DirectionsBus to "Transport"
        Screen.MORE -> Icons.Rounded.Apps to "More"
        Screen.PROFILE -> Icons.Rounded.Person to "Profile"
        Screen.EVENTS -> Icons.Rounded.Event to "Events"
        Screen.QBANK -> Icons.Rounded.Topic to "QBank"
        Screen.SOCIAL -> Icons.Rounded.People to "Social"
        Screen.FFCS_PLANNER -> Icons.Rounded.ViewTimeline to "FFCS"
        Screen.FREE_CLASSROOMS -> Icons.Rounded.MeetingRoom to "Classes"
        else -> Icons.Rounded.Circle to "Unknown"
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    contentDesc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val color = if (isSelected) colors.accent else colors.textMuted
    
    Box(
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
