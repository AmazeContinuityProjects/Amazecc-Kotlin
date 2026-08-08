package com.amazecc.app.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.strings.Strings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp

@Composable
fun BottomNavigationBar() {
    val currentScreen by AppState.currentScreen.collectAsState()
    val pinnedTabs by AppState.pinnedNavTabs.collectAsState()
    if (currentScreen == Screen.LOGIN || currentScreen == Screen.ONBOARDING) return

    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(12.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(colors.navBackground.copy(alpha = 0.92f))
            .border(1.dp, colors.accent.copy(alpha = 0.22f), CircleShape)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
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
        Screen.PROFILE -> Icons.Rounded.Person to Strings.profile
        Screen.EVENTS -> Icons.Rounded.Event to "Events"
        Screen.QBANK -> Icons.Rounded.Topic to "QBank"
        Screen.SOCIAL -> Icons.Rounded.People to "Social"
        Screen.FFCS_PLANNER -> Icons.Rounded.ViewTimeline to "FFCS"
        Screen.FREE_CLASSROOMS -> Icons.Rounded.MeetingRoom to "Classes"
        Screen.CALENDAR -> Icons.Rounded.CalendarMonth to "Calendar"
        Screen.PROJECTS -> Icons.Rounded.Folder to "Projects"
        Screen.WISHLIST -> Icons.Rounded.FavoriteBorder to "Wishlist"
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticEnabled = LocalHapticEnabled.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && isPressed) 0.88f else if (animationsEnabled && isSelected) 1.05f else 1f,
        animationSpec = bouncySpring()
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else colors.textMuted,
        animationSpec = mediumSpring()
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else Color.Transparent,
        animationSpec = mediumSpring()
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            AnimatedVisibility(
                visible = isSelected,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = contentDesc,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.sm
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
