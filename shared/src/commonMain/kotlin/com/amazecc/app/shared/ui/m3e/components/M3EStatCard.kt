package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * D-118: a stat subtitle that has to come from data the caller actually derived.
 * The raw-text constructor is private, so a UI author cannot invent an
 * unverifiable claim (e.g. "Top 5% in department") inline in a preview. Build
 * one with [fromData] for status text or [fromMetric] for a formatted metric.
 */
class M3EStatSubtitle private constructor(val text: String) {
    companion object {
        /** Wrap status text the caller computed from real app data. */
        fun fromData(text: String): M3EStatSubtitle = M3EStatSubtitle(text)

        /** Format a derived metric and its unit, e.g. "9.18" + "CGPA" -> "9.18 CGPA". */
        fun fromMetric(value: String, unit: String): M3EStatSubtitle =
            M3EStatSubtitle(if (unit.isBlank()) value else "$value $unit")
    }
}

/**
 * Data model for an M3E Stat Item (hero stat / insight tile).
 * Distinct character, bold metrics, with NO status badge per instructions.
 */
data class M3EStatItem(
    val id: String,
    val label: String,
    val value: String,
    val sub: M3EStatSubtitle? = null,
    val tone: M3EStatTone = M3EStatTone.PRIMARY,
    val icon: ImageVector? = null,
    val onClick: (() -> Unit)? = null
) {
    /**
     * Source-compatible constructor for existing call sites that still pass a
     * raw String. The string must be derived by the caller from real data;
     * new code should use [M3EStatSubtitle.fromData] / [fromMetric] directly so
     * fabricated rankings or percentiles cannot be introduced in the UI layer.
     */
    constructor(
        id: String,
        label: String,
        value: String,
        sub: String?,
        tone: M3EStatTone = M3EStatTone.PRIMARY,
        icon: ImageVector? = null,
        onClick: (() -> Unit)? = null
    ) : this(id, label, value, sub?.let { M3EStatSubtitle.fromData(it) }, tone, icon, onClick)
}

/**
 * D-118: single source of truth for a tone's accent / icon-container colours.
 * Both the standalone card and the carousel tile use this, so the two cannot
 * drift into different palettes for the same [M3EStatTone].
 */
internal fun M3EStatTone.statAccentAndIconBg(colors: M3EColorPalette): Pair<Color, Color> = when (this) {
    M3EStatTone.PRIMARY -> colors.primary to colors.primaryContainer
    M3EStatTone.SECONDARY -> colors.secondary to colors.secondaryContainer
    M3EStatTone.TERTIARY -> colors.tertiary to colors.tertiaryContainer
    M3EStatTone.GOOD -> colors.success to colors.successContainer
    M3EStatTone.WARNING -> colors.warning to colors.warningContainer
    M3EStatTone.CRITICAL -> colors.error to colors.errorContainer
    M3EStatTone.NEUTRAL -> colors.onSurface to colors.surfaceContainerHighest
}

/**
 * D-118: single source of truth for the asymmetric M3E tile shapes.
 * [complementary] produces the mirrored companion shape used by the carousel
 * next to a constant stat card; the default is the standalone card shape.
 */
internal fun M3EStatTone.statCardShape(complementary: Boolean = false): Shape = when (this) {
    M3EStatTone.PRIMARY -> if (complementary) {
        RoundedCornerShape(topStart = 10.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
    } else {
        RoundedCornerShape(topStart = 26.dp, topEnd = 10.dp, bottomEnd = 26.dp, bottomStart = 10.dp)
    }
    M3EStatTone.SECONDARY -> if (complementary) {
        RoundedCornerShape(topStart = 26.dp, topEnd = 10.dp, bottomEnd = 26.dp, bottomStart = 10.dp)
    } else {
        RoundedCornerShape(topStart = 10.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
    }
    M3EStatTone.TERTIARY -> if (complementary) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 26.dp, bottomEnd = 26.dp, bottomStart = 12.dp)
    } else {
        RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
    }
    M3EStatTone.GOOD -> if (complementary) {
        RoundedCornerShape(topStart = 26.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 26.dp)
    } else {
        RoundedCornerShape(topStart = 26.dp, topEnd = 10.dp, bottomEnd = 26.dp, bottomStart = 10.dp)
    }
    M3EStatTone.WARNING -> if (complementary) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 24.dp, bottomEnd = 20.dp, bottomStart = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 12.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    }
    M3EStatTone.CRITICAL -> if (complementary) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 24.dp, bottomStart = 10.dp)
    } else {
        RoundedCornerShape(topStart = 10.dp, topEnd = 26.dp, bottomEnd = 10.dp, bottomStart = 26.dp)
    }
    M3EStatTone.NEUTRAL -> RoundedCornerShape(20.dp)
}

/**
 * D-118: the one stat-tile body, shared by [M3EStatCard] and the carousel tile.
 * Keeps label/icon, metric and data-backed subtitle identical everywhere so
 * layout or accessibility fixes cannot be applied to only one of the two.
 */
@Composable
internal fun M3EStatCardContent(
    stat: M3EStatItem,
    accentColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(6.dp)
) {
    val colors = M3ETheme.colors

    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement
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

            val subtitle = stat.sub?.text
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
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
    val (accentColor, iconBg) = stat.tone.statAccentAndIconBg(colors)

    // D-118: route interactive cards through clickableA11y so TalkBack gets a
    // button role, focus, Enter/Space activation and an explicit action label.
    val clickableModifier = if (stat.onClick != null) {
        Modifier.clickableA11y(onClickLabel = "Open ${stat.label}") { stat.onClick?.invoke() }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(stat.tone.statCardShape())
            .background(colors.surfaceContainerLow)
            .then(clickableModifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        M3EStatCardContent(
            stat = stat,
            accentColor = accentColor,
            iconBg = iconBg,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun M3EStatCardPreview() {
    AmazeM3ETheme(isDark = false) {
        val cgpa = 9.18
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3EStatCard(
                stat = M3EStatItem(
                    id = "stat_1",
                    label = "Attendance",
                    value = "94.2%",
                    sub = M3EStatSubtitle.fromData("Safe attendance standing"),
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
                    value = cgpa.toString(),
                    sub = M3EStatSubtitle.fromMetric(cgpa.toString(), "CGPA"),
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
