package com.amazecc.app.shared.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme

// ── BUTTONS ──

@Composable
fun AmazeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    icon: ImageVector? = null
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius

    val containerColor = when (variant) {
        ButtonVariant.PRIMARY -> colors.accent
        ButtonVariant.SECONDARY -> Color.Transparent
        ButtonVariant.GHOST -> Color.Transparent
        ButtonVariant.DANGER -> colors.danger
    }

    val contentColor = when (variant) {
        ButtonVariant.PRIMARY -> if (colors.accent == Color(0xFF0EA5E9) || colors.accent == Color(0xFF8B5CF6)) Color.White else Color(0xFF111827)
        ButtonVariant.SECONDARY -> colors.textPrimary
        ButtonVariant.GHOST -> colors.textSecondary
        ButtonVariant.DANGER -> Color.White
    }

    val border = when (variant) {
        ButtonVariant.SECONDARY -> BorderStroke(1.dp, colors.border)
        else -> null
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = RoundedCornerShape(radius.small), // 12px Small Radius
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (variant == ButtonVariant.PRIMARY || variant == ButtonVariant.DANGER) colors.border else Color.Transparent,
            disabledContentColor = colors.textMuted
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        elevation = null,
        interactionSource = interactionSource
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        }
    }
}

enum class ButtonVariant {
    PRIMARY, SECONDARY, GHOST, DANGER
}

// ── CARDS ──

@Composable
fun AmazeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius

    val bg = backgroundColor ?: colors.surface
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.985f else 1f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(2.dp, RoundedCornerShape(radius.medium), clip = false)
            .clip(RoundedCornerShape(radius.medium)) // 16px Medium Radius
            .background(bg)
            .border(1.dp, colors.border, RoundedCornerShape(radius.medium))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(16.dp),
        content = content
    )
}

// ── GLASS CARD ──

@Composable
fun AmazeGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AmazeTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.glassSurface)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
            .shadow(8.dp, RoundedCornerShape(24.dp), clip = false)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(20.dp),
        content = content
    )
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    statusText: String? = null,
    statusColor: Color? = null,
    circleColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = AmazeTheme.colors
    AmazeCard(
        modifier = modifier.defaultMinSize(minWidth = 140.dp, minHeight = 120.dp),
        onClick = onClick,
        backgroundColor = colors.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(end = if (circleColor != null) 16.dp else 0.dp)) {
                Text(
                    text = title,
                    style = AmazeTheme.typography.smallLabel.copy(
                        color = colors.textMuted,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = value,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontSize = 28.sp
                        )
                    )
                    if (statusText != null) {
                        Text(
                            text = statusText,
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = statusColor ?: colors.textSecondary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                if (caption != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = caption,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            if (circleColor != null) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(circleColor)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    AmazeCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.elevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                text = title,
                style = AmazeTheme.typography.subheading.copy(
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── BADGES ──

@Composable
fun AmazeBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.INFO
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius

    val (bg, textColor) = when (variant) {
        BadgeVariant.SUCCESS -> colors.successSurface to colors.successText
        BadgeVariant.WARNING -> colors.warningSurface to colors.warningText
        BadgeVariant.DANGER -> colors.dangerSurface to colors.dangerText
        BadgeVariant.INFO -> colors.infoSurface to colors.infoText
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.small)) // Small radius for badges
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AmazeTheme.typography.smallLabel.copy(
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )
    }
}

enum class BadgeVariant {
    SUCCESS, WARNING, DANGER, INFO
}

// ── FORM INPUTS ──

@Composable
fun AmazeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius

    Column(modifier = modifier) {
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(
                color = colors.textSecondary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    style = AmazeTheme.typography.body.copy(color = colors.textMuted)
                )
            },
            leadingIcon = leadingIcon,
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(radius.small), // 12px Small Radius
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                errorBorderColor = colors.danger,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accent
            )
        )
        
        if (isError && errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorText,
                style = AmazeTheme.typography.smallLabel.copy(color = colors.danger)
            )
        }
    }
}

// ── DROPDOWN SELECT ──

@Composable
fun AmazeDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    displayMapper: (String) -> String = { it }
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(1.dp, RoundedCornerShape(radius.small))
                .clip(RoundedCornerShape(radius.small))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(radius.small))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayMapper(selectedOption),
                    style = AmazeTheme.typography.body.copy(color = colors.textPrimary)
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.textSecondary)
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayMapper(option), style = AmazeTheme.typography.body.copy(color = colors.textPrimary)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── PAGE HEADER CONTAINER ──

@Composable
fun PageHeaderContainer(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    val colors = AmazeTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)) // Semi-Pill top format rounded-b-2xl
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .padding(top = 18.dp, bottom = 18.dp, start = 24.dp, end = 24.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Black
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = AmazeTheme.typography.body.copy(
                            color = colors.textSecondary
                        )
                    )
                }
                if (actions != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        content = actions
                    )
                }
            }
        }
    }
}
