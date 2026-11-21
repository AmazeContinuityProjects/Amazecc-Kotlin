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
import androidx.compose.ui.zIndex
import com.amazecc.app.shared.domain.overhaul.M3EDestination
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-131: the two compact-window navigation treatments.
 *
 * - [EXPANDING] is the restored M3E pill: unselected destinations are icon-only
 *   and the selected one animates its label in with the project's motion tokens.
 * - [LABELLED_DOCKED] keeps every label visible and docks the bar flush to the
 *   scaffold's inset bottom edge, full width.
 *
 * The expanded-window rail ignores this value: it is always a labelled vertical
 * rail so wide windows keep their established layout.
 */
enum class M3ENavBarStyle { EXPANDING, LABELLED_DOCKED }

/**
 * Material 3 Expressive floating navigation, sourced from M3E specification §9.6.
 *
 * D-131: [M3ENavBarStyle.EXPANDING] is the default so users get the nice old
 * behaviour back; [M3ENavBarStyle.LABELLED_DOCKED] preserves the always-labelled
 * bar for anyone who prefers it. Every item stays a labelled [clickableA11y]
 * target of at least 48 dp: when the text is hidden the destination name is
 * still exposed as the merged node's content description.
 */
@Composable
fun M3EFloatingToolbar(
    currentDestination: M3EDestination,
    onDestinationSelected: (M3EDestination) -> Unit,
    modifier: Modifier = Modifier,
    useRail: Boolean = false,
    style: M3ENavBarStyle = M3ENavBarStyle.EXPANDING,
    destinations: List<M3EDestination> = M3EDestination.entries
) {
    val colors = M3ETheme.colors
    // A docked bar is a surface that meets the bottom edge; the expanding bar
    // stays a floating pill with all four corners rounded and no shadow.
    val docked = !useRail && style == M3ENavBarStyle.LABELLED_DOCKED
    val containerShape = if (docked) {
        RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
    } else {
        RoundedCornerShape(28.dp)
    }

    if (useRail) {
        // Expanded windows: vertical rail. The host aligns this to CenterStart
        // and reserves space for it so content never sits underneath.
        Box(
            modifier = modifier
                .zIndex(101f)
                .clip(containerShape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), containerShape)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                destinations.forEach { destination ->
                    M3EToolbarItem(
                        destination = destination,
                        icon = iconFor(destination),
                        isSelected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        rail = true,
                        style = style
                    )
                }
            }
        }
    } else {
        // Compact / medium windows: horizontal bar. Docked fills the width and
        // drops the toolbar's own gutter; expanding keeps the floating pill.
        Box(
            modifier = modifier
                .zIndex(101f)
                .fillMaxWidth()
                .padding(
                    horizontal = if (docked) 0.dp else 8.dp,
                    vertical = if (docked) 0.dp else 12.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .then(if (docked) Modifier.fillMaxWidth() else Modifier)
                    .clip(containerShape)
                    .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), containerShape)
                    .padding(horizontal = 6.dp, vertical = if (docked) 10.dp else 6.dp),
                horizontalArrangement = if (docked) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.spacedBy(2.dp)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    M3EToolbarItem(
                        destination = destination,
                        icon = iconFor(destination),
                        isSelected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        rail = false,
                        style = style
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
    rail: Boolean,
    style: M3ENavBarStyle
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

    // D-131: only the expanding compact pill animates its horizontal padding;
    // docked compact items and rail items keep their fixed, stable footprint.
    val expandingPill = !rail && style == M3ENavBarStyle.EXPANDING
    val horizontalPadding by animateDpAsState(
        targetValue = when {
            expandingPill && isSelected -> 20.dp
            expandingPill -> 16.dp
            else -> 8.dp
        },
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "ToolbarItemPadding"
    )

    // 56 dp tall / 60–168 dp wide (56 dp icon-only when expanding): the touch
    // target includes the padding because clickableA11y is applied before it.
    val itemModifier = Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(if (expandingPill) RoundedCornerShape(28.dp) else RoundedCornerShape(18.dp))
        .background(activeBgColor)
        .clickableA11y(
            onClickLabel = destination.label,
            interactionSource = interactionSource,
            onClick = onClick
        )
        .defaultMinSize(
            minWidth = when {
                rail -> 168.dp
                expandingPill -> 56.dp
                else -> 60.dp
            },
            minHeight = 56.dp
        )
        .padding(
            horizontal = if (expandingPill) horizontalPadding else 8.dp,
            vertical = 6.dp
        )

    when {
        rail -> {
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
        }

        style == M3ENavBarStyle.LABELLED_DOCKED -> {
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

        else -> {
            // D-131: restored expanding pill — unselected items are icon-only,
            // the selected item animates its label in with the M3E motion tokens.
            Row(
                modifier = itemModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // parent clickableA11y owns the label
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
                        text = destination.label,
                        color = contentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
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

@Preview
@Composable
private fun M3EFloatingToolbarDockedPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EFloatingToolbar(
            currentDestination = M3EDestination.HOME,
            onDestinationSelected = {},
            style = M3ENavBarStyle.LABELLED_DOCKED
        )
    }
}
