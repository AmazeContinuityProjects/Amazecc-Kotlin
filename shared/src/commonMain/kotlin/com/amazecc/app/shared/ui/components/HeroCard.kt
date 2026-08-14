package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme

/**
 * Palette of colors used inside a [HeroCard]. When hero colors are enabled the
 * palette derives from [AmazeColors.onAccent] (white in dark mode, near-black in
 * light mode) so text/pies stay readable on the gradient; when disabled the card
 * falls back to a neutral surface and the accent returns to progress/ring colors
 * plus a subtle accent border.
 */
class HeroPalette(
    val background: Brush,
    val border: Color?,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statLabel: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val panelBg: Color,
    val panelBorder: Color,
    val divider: Color,
    val progress: Color,
    val progressTrack: Color,
    val iconBg: Color
)

@Composable
fun heroPaletteFor(colors: AmazeColors, tint: Color, enabled: Boolean): HeroPalette {
    return if (enabled) {
        val on = colors.onAccent
        HeroPalette(
            background = Brush.linearGradient(colors = listOf(tint, tint.copy(alpha = 0.6f))),
            border = null,
            text = on,
            textSecondary = on.copy(alpha = 0.9f),
            textMuted = on.copy(alpha = 0.75f),
            statLabel = on.copy(alpha = 0.8f),
            chipBg = on.copy(alpha = 0.18f),
            chipBorder = on.copy(alpha = 0.3f),
            panelBg = on.copy(alpha = 0.14f),
            panelBorder = on.copy(alpha = 0.2f),
            divider = on.copy(alpha = 0.25f),
            progress = on,
            progressTrack = on.copy(alpha = 0.25f),
            iconBg = on.copy(alpha = 0.2f)
        )
    } else {
        HeroPalette(
            background = SolidColor(colors.surface),
            border = tint.copy(alpha = 0.25f),
            text = colors.textPrimary,
            textSecondary = colors.textSecondary,
            textMuted = colors.textMuted,
            statLabel = colors.textSecondary,
            chipBg = tint.copy(alpha = 0.10f),
            chipBorder = tint.copy(alpha = 0.30f),
            panelBg = tint.copy(alpha = 0.06f),
            panelBorder = tint.copy(alpha = 0.18f),
            divider = colors.border,
            progress = tint,
            progressTrack = tint.copy(alpha = 0.15f),
            iconBg = tint.copy(alpha = 0.12f)
        )
    }
}

/**
 * Shared hero-card prototype: an accent gradient card whose whole palette
 * (text, chips, panels, pies, borders) is exposed through a [HeroPalette].
 * When hero colors are disabled it becomes a neutral surface card with a thin
 * accent border — content keeps working with zero per-element branching.
 *
 * @param tint color used for the gradient (and the accent border when disabled);
 *        defaults to the theme accent.
 * @param spacing vertical gap between content rows.
 */
@Composable
fun HeroCard(
    colors: AmazeColors = AmazeTheme.colors,
    tint: Color = colors.accent,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.(HeroPalette) -> Unit
) {
    val p = heroPaletteFor(colors, tint, AmazeTheme.heroColorEnabled)
    val shape = RoundedCornerShape(AmazeTheme.radius.large)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(p.background)
            .then(if (p.border != null) Modifier.border(1.dp, p.border, shape) else Modifier)
            .padding(contentPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            content(p)
        }
    }
}

/** Small badge chip sitting on a hero card (e.g. "3 OVERDUE"). */
@Composable
fun HeroChip(
    text: String,
    p: HeroPalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
            .background(p.chipBg)
            .border(1.dp, p.chipBorder, RoundedCornerShape(AmazeTheme.radius.xs))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = p.text, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
    }
}

/** Frosted inner panel used to group content inside a hero card. */
@Composable
fun HeroPanel(
    p: HeroPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(p.panelBg)
            .border(1.dp, p.panelBorder, RoundedCornerShape(AmazeTheme.radius.medium))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

/** Label + value pair for hero-card stat rows. */
@Composable
fun HeroStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = AmazeTheme.fontSize.lg
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = valueSize, color = color, maxLines = 1)
        Text(label, fontSize = AmazeTheme.fontSize.micro, color = color.copy(alpha = 0.7f), maxLines = 1)
    }
}
