package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive floating navigation, sourced from M3E specification §9.6.
 *
 * D-119: this is the *single* nav implementation used by [M3EFloatingToolbar]'s
 * host scaffold. It renders a compact horizontal bar (icon above label, like a
 * Material NavigationBar) on small windows and a vertical rail on expanded
 * windows. Every item is a labelled [clickableA11y] target of at least 48 dp;
 * labels are always visible so icon-only buttons never regress again.
 */
@Composable
fun M3EFloatingToolbar(
    currentDestination: M3EDestination,
    onDestinationSelected: (M3EDestination) -> Unit,
    modifier: Modifier = Modifier,
    useRail: Boolean = false
) {
    val colors = M3ETheme.colors
    val containerShape = RoundedCornerShape(28.dp)

    if (useRail) {
        // Expanded windows: vertical rail. The host aligns this to CenterStart
        // and reserves space for it so content never sits underneath.
        Box(
            modifier = modifier
                .clip(containerShape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), containerShape)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                M3EDestination.entries.forEach { destination ->
                    M3EToolbarItem(
                        destination = destination,
                        icon = iconFor(destination),
                        isSelected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        rail = true
                    )
                }
            }
        }
    } else {
        // Compact / medium windows: horizontal floating bar.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(containerShape)
                    .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), containerShape)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                M3EDestination.entries.forEach { destination ->
                    M3EToolbarItem(
                        destination = destination,
                        icon = iconFor(destination),
                        isSelected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        rail = false
                    )
                }
            }
        }
    }
}

private fun iconFor(destination: M3EDestination): ImageVector = when (destination) {
    M3EDestination.HOME -> Icons.Rounded.Home
    M3EDestination.ATTENDANCE -> Icons.Rounded.CheckCircle
    M3EDestination.TIMETABLE -> Icons.Rounded.CalendarMonth
    M3EDestination.ACADEMICS -> Icons.Rounded.School
    M3EDestination.MORE -> Icons.Rounded.MoreHoriz
}

@Composable
private fun M3EToolbarItem(
    destination: M3EDestination,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    rail: Boolean
) {
    val colors = M3ETheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
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

    // 56 dp tall / 60 dp (bar) or 168 dp (rail) wide: the touch target includes
    // the padding because clickableA11y is applied before the padding modifier.
    val itemModifier = Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(RoundedCornerShape(18.dp))
        .background(activeBgColor)
        .clickableA11y(
            onClickLabel = destination.label,
            interactionSource = interactionSource,
            onClick = onClick
        )
        .defaultMinSize(
            minWidth = if (rail) 168.dp else 60.dp,
            minHeight = 56.dp
        )
        .padding(horizontal = 8.dp, vertical = 6.dp)

    if (rail) {
        Row(
            modifier = itemModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // parent clickableA11y owns the label
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = destination.label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        Column(
            modifier = itemModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // parent clickableA11y owns the label
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = destination.label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Preview
@Composable
private fun M3EFloatingToolbarPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EFloatingToolbar(
            currentDestination = M3EDestination.HOME,
            onDestinationSelected = {}
        )
    }
}

@Preview
@Composable
private fun M3EFloatingToolbarDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EFloatingToolbar(
            currentDestination = M3EDestination.ATTENDANCE,
            onDestinationSelected = {}
        )
    }
}

@Preview
@Composable
private fun M3EFloatingToolbarRailPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EFloatingToolbar(
            currentDestination = M3EDestination.TIMETABLE,
            onDestinationSelected = {},
            useRail = true
        )
    }
}
