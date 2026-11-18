package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EDestination
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/**
 * Material 3 Expressive Horizontal Floating Toolbar (Navigation Bar).
 * Sourced directly from M3E specification §9.6:
 * - Shorter flexible height
 * - Pill-shaped active indicator in contrasting color
 * - Active label in secondary color
 * - No drop shadow, clean surfaceContainerHighest backdrop with border.
 */
@Composable
fun M3EFloatingToolbar(
    currentDestination: M3EDestination,
    onDestinationSelected: (M3EDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3EDestination.entries.forEach { destination ->
                val isSelected = destination == currentDestination
                val icon = when (destination) {
                    M3EDestination.HOME -> Icons.Rounded.Home
                    M3EDestination.ATTENDANCE -> Icons.Rounded.CheckCircle
                    M3EDestination.TIMETABLE -> Icons.Rounded.CalendarMonth
                    M3EDestination.ACADEMICS -> Icons.Rounded.School
                    M3EDestination.MORE -> Icons.Rounded.MoreHoriz
                }

                M3EToolbarItem(
                    destination = destination,
                    icon = icon,
                    isSelected = isSelected,
                    onClick = { onDestinationSelected(destination) }
                )
            }
        }
    }
}

@Composable
private fun M3EToolbarItem(
    destination: M3EDestination,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = M3ETheme.motion.spatialFast(),
        label = "ToolbarItemScale"
    )

    val activeBgColor by animateColorAsState(
        targetValue = if (isSelected) colors.secondaryContainer else colors.surfaceContainerHighest.copy(alpha = 0f),
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "ToolbarItemBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        animationSpec = M3ETheme.motion.effectsDefault(),
        label = "ToolbarItemContent"
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = destination.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            if (isSelected) {
                Text(
                    text = destination.label,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
