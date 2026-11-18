package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive (M3E) Floating Pill Navigation Bar.
 * Sourced directly from M3E specification:
 * - Floating pill navigation bar with surfaceContainerHigh or surfaceContainerHighest
 * - Border: outlineVariant with 0.4 alpha
 * - Elevation: Level 2 (3dp)
 * - Expressive active pill indicators using spring physics (M3ETheme.motion.spatialDefault())
 * - Selected pill: secondaryContainer background with onSecondaryContainer text/icon color
 * - Unselected: transparent background with onSurfaceVariant text/icon color
 * - Zero hardcoded Color.White / Color.Black tokens
 */
@Composable
fun M3ENavigationBar(
    modifier: Modifier = Modifier,
    currentScreen: Screen = AppState.currentScreen.collectAsState().value,
    pinnedTabs: List<Screen> = AppState.pinnedNavTabs.collectAsState().value,
    onTabSelected: (Screen) -> Unit = { AppState.navigateTo(it) }
) {
    if (currentScreen == Screen.LOGIN || currentScreen == Screen.SPLASH || currentScreen == Screen.ONBOARDING) {
        return
    }

    val tabs = remember(pinnedTabs) {
        val list = mutableListOf(Screen.HOME)
        if (pinnedTabs.isEmpty()) {
            list.addAll(listOf(Screen.ATTENDANCE, Screen.ACADEMICS, Screen.PROFILE))
        } else {
            pinnedTabs.forEach { tab ->
                if (tab != Screen.HOME && tab != Screen.MORE && tab !in list) {
                    list.add(tab)
                }
            }
        }
        if (Screen.MORE !in list) {
            list.add(Screen.MORE)
        }
        list
    }

    M3ENavigationBarContent(
        tabs = tabs,
        currentScreen = currentScreen,
        onTabSelected = onTabSelected,
        modifier = modifier
    )
}

@Composable
fun M3ENavigationBar(
    tabs: List<Screen>,
    currentScreen: Screen,
    onTabSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    M3ENavigationBarContent(
        tabs = tabs,
        currentScreen = currentScreen,
        onTabSelected = onTabSelected,
        modifier = modifier
    )
}

@Composable
private fun M3ENavigationBarContent(
    tabs: List<Screen>,
    currentScreen: Screen,
    onTabSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val (icon, label) = getNavTabIconAndLabel(tab)
                val isSelected = currentScreen == tab

                M3ENavItemPill(
                    icon = icon,
                    label = label,
                    isSelected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun M3ENavItemPill(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else if (isSelected) 1.02f else 1.0f,
        animationSpec = M3ETheme.motion.spatialFast(),
        label = "M3ENavItemScale"
    )

    val activeBgColor by animateColorAsState(
        targetValue = if (isSelected) colors.secondaryContainer else Color.Transparent,
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "M3ENavItemBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "M3ENavItemContent"
    )

    val horizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 10.dp,
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "M3ENavItemPadding"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(activeBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            AnimatedVisibility(
                visible = isSelected,
                enter = expandHorizontally(animationSpec = M3ETheme.motion.spatialDefault()) +
                        fadeIn(animationSpec = M3ETheme.motion.effectsDefault()),
                exit = shrinkHorizontally(animationSpec = M3ETheme.motion.spatialDefault()) +
                        fadeOut(animationSpec = M3ETheme.motion.effectsDefault())
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

fun getNavTabIconAndLabel(screen: Screen): Pair<ImageVector, String> {
    return when (screen) {
        Screen.HOME -> Icons.Rounded.Home to "Home"
        Screen.ATTENDANCE -> Icons.Rounded.EventAvailable to "Attendance"
        Screen.ACADEMICS -> Icons.Rounded.School to "Academics"
        Screen.PROFILE -> Icons.Rounded.Person to "Profile"
        Screen.MORE -> Icons.Rounded.Apps to "More"
        Screen.LIBRARIES -> Icons.AutoMirrored.Rounded.LibraryBooks to "Library"
        Screen.HOSTEL -> Icons.Rounded.Apartment to "Hostel"
        Screen.CABSHARE -> Icons.Rounded.DirectionsCar to "Cabshare"
        Screen.TRANSPORT -> Icons.Rounded.DirectionsBus to "Transport"
        Screen.EVENTS -> Icons.Rounded.Event to "Events"
        Screen.QBANK -> Icons.Rounded.Topic to "QBank"
        Screen.SOCIAL -> Icons.Rounded.People to "Social"
        Screen.FFCS_PLANNER -> Icons.Rounded.ViewTimeline to "FFCS"
        Screen.FREE_CLASSROOMS -> Icons.Rounded.MeetingRoom to "Classes"
        Screen.CALENDAR -> Icons.Rounded.CalendarMonth to "Calendar"
        Screen.PAYMENTS -> Icons.Rounded.CreditCard to "Payments"
        else -> Icons.Rounded.Circle to "Item"
    }
}

@Preview
@Composable
private fun M3ENavigationBarLightPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ENavigationBar(
            tabs = listOf(Screen.HOME, Screen.ATTENDANCE, Screen.ACADEMICS, Screen.PROFILE, Screen.MORE),
            currentScreen = Screen.HOME,
            onTabSelected = {}
        )
    }
}

@Preview
@Composable
private fun M3ENavigationBarDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ENavigationBar(
            tabs = listOf(Screen.HOME, Screen.ATTENDANCE, Screen.ACADEMICS, Screen.PROFILE, Screen.MORE),
            currentScreen = Screen.ATTENDANCE,
            onTabSelected = {}
        )
    }
}
