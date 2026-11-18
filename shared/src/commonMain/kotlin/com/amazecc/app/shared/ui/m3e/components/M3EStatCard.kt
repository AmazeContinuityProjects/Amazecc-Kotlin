package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Data model for an M3E Stat Item (hero stat / insight tile).
 * Distinct character, bold metrics, with NO status badge per instructions.
 */
data class M3EStatItem(
    val id: String,
    val label: String,
    val value: String,
    val sub: String? = null,
    val tone: M3EStatTone = M3EStatTone.PRIMARY,
    val icon: ImageVector? = null,
    val onClick: (() -> Unit)? = null
)

/**
 * Material 3 Expressive Stat Card with strong geometric character and shape variety.
 * Each tone receives an asymmetric organic shape inspired by M3E shape tokens.
 * Badge is explicitly omitted to maintain clean, focused hero metrics.
 */
@Composable
fun M3EStatCard(
    stat: M3EStatItem,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    // Asymmetric shapes with character based on the semantic tone
    val cardShape = when (stat.tone) {
        M3EStatTone.PRIMARY -> RoundedCornerShape(topStart = 28.dp, topEnd = 10.dp, bottomEnd = 28.dp, bottomStart = 10.dp)
        M3EStatTone.SECONDARY -> RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp)
        M3EStatTone.TERTIARY -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 10.dp, bottomStart = 28.dp)
        M3EStatTone.GOOD -> RoundedCornerShape(topStart = 14.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 14.dp)
        M3EStatTone.WARNING -> RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
        M3EStatTone.CRITICAL -> RoundedCornerShape(topStart = 10.dp, topEnd = 28.dp, bottomEnd = 10.dp, bottomStart = 28.dp)
        M3EStatTone.NEUTRAL -> RoundedCornerShape(20.dp)
    }

    val (bgContainer, accentColor, iconBg) = when (stat.tone) {
        M3EStatTone.PRIMARY -> Triple(colors.surfaceContainerLow, colors.primary, colors.primaryContainer)
        M3EStatTone.SECONDARY -> Triple(colors.surfaceContainerLow, colors.secondary, colors.secondaryContainer)
        M3EStatTone.TERTIARY -> Triple(colors.surfaceContainerLow, colors.tertiary, colors.tertiaryContainer)
        M3EStatTone.GOOD -> Triple(colors.surfaceContainerLow, colors.success, colors.successContainer)
        M3EStatTone.WARNING -> Triple(colors.surfaceContainerLow, colors.warning, colors.warningContainer)
        M3EStatTone.CRITICAL -> Triple(colors.surfaceContainerLow, colors.error, colors.errorContainer)
        M3EStatTone.NEUTRAL -> Triple(colors.surfaceContainerLow, colors.onSurface, colors.surfaceContainerHighest)
    }

    val clickableModifier = if (stat.onClick != null) {
        Modifier.clickable(onClick = stat.onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(bgContainer)
            .then(clickableModifier)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Label row with optional expressive icon container
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )

                if (stat.icon != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = stat.icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Big Bold Hero Metric Value
            Text(
                text = stat.value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = accentColor,
                fontSize = 32.sp,
                letterSpacing = (-0.5).sp
            )

            // Subline Description
            if (!stat.sub.isNullOrBlank()) {
                Text(
                    text = stat.sub,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Preview
@Composable
private fun M3EStatCardPreview() {
    AmazeM3ETheme(isDark = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EStatCard(
                stat = M3EStatItem(
                    id = "stat_1",
                    label = "Cumulative GPA",
                    value = "9.18",
                    sub = "Top 5% in School of Computer Science",
                    tone = M3EStatTone.PRIMARY,
                    icon = Icons.Rounded.School
                )
            )
            M3EStatCard(
                stat = M3EStatItem(
                    id = "stat_2",
                    label = "Overall Attendance",
                    value = "94.2%",
                    sub = "Safe margin: can miss 14 more lectures",
                    tone = M3EStatTone.GOOD,
                    icon = Icons.Rounded.Insights
                )
            )
        }
    }
}
