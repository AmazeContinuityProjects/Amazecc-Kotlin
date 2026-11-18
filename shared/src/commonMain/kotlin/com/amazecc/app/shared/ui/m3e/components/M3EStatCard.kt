package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
 * Optimized to fit comfortably in half-screen multi-card rows without text wrapping or clipping.
 * Each tone receives an asymmetric organic shape inspired by M3E shape tokens.
 */
@Composable
fun M3EStatCard(
    stat: M3EStatItem,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    // Asymmetric shapes with character based on the semantic tone
    val cardShape = when (stat.tone) {
        M3EStatTone.PRIMARY -> RoundedCornerShape(topStart = 26.dp, topEnd = 10.dp, bottomEnd = 26.dp, bottomStart = 10.dp)
        M3EStatTone.SECONDARY -> RoundedCornerShape(topStart = 10.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
        M3EStatTone.TERTIARY -> RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
        M3EStatTone.GOOD -> RoundedCornerShape(topStart = 26.dp, topEnd = 10.dp, bottomEnd = 26.dp, bottomStart = 10.dp)
        M3EStatTone.WARNING -> RoundedCornerShape(topStart = 24.dp, topEnd = 12.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
        M3EStatTone.CRITICAL -> RoundedCornerShape(topStart = 10.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
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
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                    letterSpacing = 1.sp,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (stat.icon != null) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = stat.icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Big Bold Hero Metric Value & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    fontSize = 24.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1
                )

                if (!stat.sub.isNullOrBlank()) {
                    Text(
                        text = stat.sub,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun M3EStatCardPreview() {
    AmazeM3ETheme(isDark = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EStatCard(
                stat = M3EStatItem(
                    id = "stat_1",
                    label = "Attendance",
                    value = "94.2%",
                    sub = "Safe attendance standing",
                    tone = M3EStatTone.GOOD,
                    icon = Icons.Rounded.CheckCircle
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(136.dp)
            )
            M3EStatCard(
                stat = M3EStatItem(
                    id = "stat_2",
                    label = "CGPA",
                    value = "9.18",
                    sub = "Top 5% in department",
                    tone = M3EStatTone.PRIMARY,
                    icon = Icons.Rounded.School
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(136.dp)
            )
        }
    }
}
