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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme

val BOTTOM_NAV_PADDING = 88.dp

@Composable
fun FooterSpacer(
    modifier: Modifier = Modifier,
    height: Dp = BOTTOM_NAV_PADDING
) {
    Spacer(modifier = modifier.fillMaxWidth().height(height))
}

// ── BUTTONS ──

@Composable
fun AmazeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    height: Dp = 48.dp
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
    val hapticEnabled = LocalHapticEnabled.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && isPressed) 0.95f else 1f,
        animationSpec = bouncySpring()
    )

    val spacing = AmazeTheme.spacing

    Button(
        onClick = {
            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.height(height).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (variant == ButtonVariant.PRIMARY || variant == ButtonVariant.DANGER) colors.border else Color.Transparent,
            disabledContentColor = colors.textMuted
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs),
        elevation = null,
        interactionSource = interactionSource
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(spacing.sm))
            }
            Text(
                text = text,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class ButtonVariant {
    PRIMARY, SECONDARY, GHOST, DANGER
}

// ── CARDS ──

enum class CardVariant {
    DEFAULT, ACCENT, ACCENT_SURFACE
}

@Composable
fun AmazeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    variant: CardVariant = CardVariant.DEFAULT,
    accentStrip: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticEnabled = LocalHapticEnabled.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && isPressed) 0.96f else 1f,
        animationSpec = bouncySpring()
    )

    val borderAlpha = 0.4f
    val (bgColor, borderColor) = when (variant) {
        CardVariant.DEFAULT -> (backgroundColor ?: colors.surface) to colors.textMuted.copy(alpha = borderAlpha)
        CardVariant.ACCENT -> (backgroundColor ?: colors.accentSurface) to colors.accent.copy(alpha = borderAlpha)
        CardVariant.ACCENT_SURFACE -> (backgroundColor ?: colors.accentContainer) to colors.accent.copy(alpha = 0.45f)
    }

    val spacing = AmazeTheme.spacing

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clipToBounds()
            .shadow(1.dp, RoundedCornerShape(radius.medium), clip = false)
            .clip(RoundedCornerShape(radius.medium))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(radius.medium))
            .then(
                if (accentStrip) {
                    Modifier.drawBehind {
                        val stripWidth = 4.dp.toPx()
                        drawRoundRect(
                            color = colors.accent,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(stripWidth, size.height)
                        )
                    }
                } else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                } else {
                    Modifier
                }
            )
            .padding(spacing.cardPadding),
        content = content
    )
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
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AmazeTheme.typography.smallLabel.copy(
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = AmazeTheme.fontSize.xs
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
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
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(radius.small),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
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

