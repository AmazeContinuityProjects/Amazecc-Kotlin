package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeTheme

@Composable
fun SettingsGroupLabel(text: String, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    Text(
        text = text.uppercase(),
        style = AmazeTheme.typography.smallLabel.copy(
            color = colors.textMuted,
            fontWeight = FontWeight.Black,
            fontSize = AmazeTheme.fontSize.xs,
            letterSpacing = 1.2.sp
        ),
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AmazeTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(if (danger) colors.danger.copy(alpha = 0.05f) else colors.elevatedSurface)
            .border(
                width = 1.dp,
                color = if (danger) colors.danger.copy(alpha = 0.25f) else colors.border,
                shape = RoundedCornerShape(AmazeTheme.radius.large)
            )
            .padding(vertical = 2.dp),
    ) {
        content()
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    tint: Color,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AmazeTheme.typography.body.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (danger) colors.dangerText else colors.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = AmazeTheme.typography.smallLabel.copy(
                    color = if (danger) colors.dangerText else colors.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = AmazeTheme.fontSize.xs
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = if (danger) colors.dangerText.copy(alpha = 0.5f) else colors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    HorizontalDivider(
        modifier = modifier.padding(start = 58.dp),
        color = colors.border.copy(alpha = 0.6f)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AmazeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accent,
                checkedTrackColor = colors.accent.copy(alpha = 0.3f)
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}
