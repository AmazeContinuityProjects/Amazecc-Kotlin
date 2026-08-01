package com.amazecc.app.shared.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── FEATURE TOGGLE COMPOSITION LOCALS ──

/**
 * When true, haptic feedback fires on button presses, card clicks, and nav tab switches.
 * Controlled by the user preference in Settings → Interactions.
 */
val LocalHapticEnabled = compositionLocalOf { true }

/**
 * When true, bouncy spring press-scale animations play on interactive elements.
 * Controlled by the user preference in Settings → Interactions.
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

/**
 * Provides both interaction toggles at once from AppState StateFlows.
 * Called at the AmazeTheme composition root so all children inherit it.
 */
@Composable
fun ProvideInteractionPrefs(
    hapticEnabled: Boolean,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalHapticEnabled provides hapticEnabled,
        LocalAnimationsEnabled provides animationsEnabled,
        content = content
    )
}

// ── SPRING SPECS ──

private val _bouncySpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioHighBouncy,
    stiffness = Spring.StiffnessMedium
)
private val _mediumSpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * High bouncy spring spec for juicy press and tap interactions.
 * Cached instance to avoid per-frame allocation.
 */
@Suppress("UNCHECKED_CAST")
fun <T> bouncySpring(): SpringSpec<T> = _bouncySpring as SpringSpec<T>

/**
 * Smooth medium bouncy spring spec for tab switches and expansions.
 * Cached instance to avoid per-frame allocation.
 */
@Suppress("UNCHECKED_CAST")
fun <T> mediumSpring(): SpringSpec<T> = _mediumSpring as SpringSpec<T>

// ── INTERACTION MODIFIERS ──

/**
 * A combined modifier that applies a bouncy press-scale animation (if animations are enabled)
 * and fires haptic feedback (if haptics are enabled) on click.
 *
 * Use this as a drop-in replacement for [Modifier.clickable] on custom card surfaces and containers
 * that are NOT already wrapped in [AmazeButton] or [AmazeCard].
 */
@Composable
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier {
    val hapticEnabled = LocalHapticEnabled.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && isPressed) 0.95f else 1f,
        animationSpec = bouncySpring()
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        )
}

// ── GLOW EFFECT ──

/**
 * Custom modifier to add a subtle ambient glow behind an element.
 */
fun Modifier.subtleGlow(
    color: Color,
    radius: Dp = 12.dp,
    alpha: Float = 0.2f
): Modifier = this.drawBehind {
    if (alpha <= 0f) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color.copy(alpha = alpha)
        }
        canvas.drawCircle(
            center = center,
            radius = size.minDimension / 2f + radius.toPx(),
            paint = paint
        )
    }
}

// ── COURSE COLOR CODING ──

/**
 * Generates a deterministic course color accent from a course code.
 * Maps the hash code of the course ID onto the theme's chart palette (chart1..chart5).
 */
fun courseColor(courseCode: String, colors: com.amazecc.app.shared.theme.AmazeColors): Color {
    val palette = listOf(colors.chart1, colors.chart2, colors.chart3, colors.chart4, colors.chart5)
    val hash = courseCode.uppercase().fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return palette[hash % palette.size]
}

// ── TEXT UTILITIES ──

/**
 * Shortens long assessment names into standard crisp acronyms:
 * - "Continuous Assessment Test - I" -> "CAT - I"
 * - "Continuous Assessment Test - II" -> "CAT - II"
 * - "Formative Assessment Test" -> "FAT"
 * - "Final Assessment Test" -> "FAT"
 * - "Digital Assignment" -> "DA"
 */
fun shortenAssessmentName(name: String): String {
    return name
        .replace("Continuous Assessment Test - I", "CAT - I", ignoreCase = true)
        .replace("Continuous Assessment Test - II", "CAT - II", ignoreCase = true)
        .replace("Continuous Assessment Test-I", "CAT - I", ignoreCase = true)
        .replace("Continuous Assessment Test-II", "CAT - II", ignoreCase = true)
        .replace("Continuous Assessment Test 1", "CAT - I", ignoreCase = true)
        .replace("Continuous Assessment Test 2", "CAT - II", ignoreCase = true)
        .replace("Continuous Assessment Test", "CAT", ignoreCase = true)
        .replace("Formative Assessment Test", "FAT", ignoreCase = true)
        .replace("Final Assessment Test", "FAT", ignoreCase = true)
        .replace("Digital Assignment", "DA", ignoreCase = true)
}
