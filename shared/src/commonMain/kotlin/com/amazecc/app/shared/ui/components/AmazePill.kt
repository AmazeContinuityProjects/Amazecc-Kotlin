package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeTheme

/**
 * Design-language pill chip: icon + label, tinted when selected.
 * Used across the app for single-choice selectors (task type, priority, etc).
 */
@Composable
fun AmazePill(
    label: String,
    selected: Boolean,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val pillColor = tint ?: colors.accent
    val container = if (selected) pillColor.copy(alpha = 0.15f) else colors.surface
    val borderColor = if (selected) pillColor.copy(alpha = 0.45f) else colors.textMuted.copy(alpha = 0.25f)
    val contentColor = if (selected) pillColor else colors.textSecondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(container)
            .border(1.dp, borderColor, RoundedCornerShape(AmazeTheme.radius.medium))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            label,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = AmazeTheme.fontSize.xs
        )
    }
}
