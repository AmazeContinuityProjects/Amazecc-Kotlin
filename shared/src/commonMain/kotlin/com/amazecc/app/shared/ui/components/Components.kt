package com.amazecc.app.shared.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme

import androidx.compose.ui.draw.blur

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

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
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
        elevation = null
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

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    statusText: String? = null,
    statusColor: Color? = null,
    onClick: (() -> Unit)? = null,
    isBlur: Boolean = false
) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = modifier, onClick = onClick, backgroundColor = colors.surface) {
        Column {
            Text(
                text = title,
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBlur) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .width(68.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(colors.textSecondary.copy(alpha = 0.25f))
                    )
                } else {
                    Text(
                        text = value,
                        style = AmazeTheme.typography.display.copy(
                            color = colors.textPrimary,
                            fontSize = 28.sp
                        )
                    )
                }
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = caption,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
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
                imageVector = Icons.Rounded.ArrowForward,
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
    isPassword: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    var passwordVisible by remember { mutableStateOf(false) }

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
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = colors.textMuted
                        )
                    }
                }
            } else {
                null
            },
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
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
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = AmazeTheme.typography.smallLabel.copy(
                color = colors.textSecondary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedOption,
                    style = AmazeTheme.typography.body.copy(color = colors.textPrimary)
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(colors.elevatedSurface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary)
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
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
            .padding(top = 24.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
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
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black // font-black weight Outfit
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                }
                if (actions != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}
