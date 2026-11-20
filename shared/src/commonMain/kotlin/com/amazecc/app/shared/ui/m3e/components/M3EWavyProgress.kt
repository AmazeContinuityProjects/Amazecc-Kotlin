package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pass this as [progress] to draw an honest indeterminate loading wave (no fake percentage). */
const val M3E_WAVY_INDETERMINATE = -1f

/**
 * Material 3 Expressive Wavy Progress Indicator (The signature M3E visual).
 * Sourced directly from M3E specification §9.1:
 * - 3dp amplitude sine wave
 * - 40dp wavelength
 * - 4dp stroke thickness
 * - 4dp gap between active wave and flat track
 * - 4dp stop dot at end of track
 *
 * Accessibility (D-118): the canvas publishes a quantity-specific
 * [contentDescription] from [label], a [stateDescription] (percent plus the
 * safety verdict, or "Loading" when indeterminate) and a
 * [progressBarRangeInfo] for determinate values. No role constant is set:
 * this Compose artifact does not expose one for progress indicators. A visible
 * 75% tick plus a safe/unsafe tone answer "am I safe?" without relying on
 * colour alone.
 *
 * @param label Announced name of the measured quantity, e.g. "Attendance",
 *   "Assignment accuracy" or "Exam preparation". Callers must supply a
 *   quantity-specific label. It is required so the compiler blocks any new
 *   generic-label regression.
 * @param safetyThreshold Fraction at which the value is considered safe.
 *   Pass a value outside 0..1 is clamped; the default 0.75 matches the
 *   attendance target used across AmazeCC.
 */
@Composable
fun M3EWavyLinearProgress(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    activeColor: Color? = null,
    trackColor: Color? = null,
    label: String,
    safetyThreshold: Float = 0.75f
) {
    val colors = M3ETheme.colors
    val indeterminate = progress < 0f
    val value = if (indeterminate) 0f else progress.coerceIn(0f, 1f)
    val threshold = safetyThreshold.coerceIn(0f, 1f)
    val isSafe = value >= threshold
    // Indeterminate loading uses the neutral primary colour — never a fake safe/error verdict.
    val effectiveActive = if (indeterminate) colors.primary else (activeColor ?: if (isSafe) colors.success else colors.error)

    // D-118: the old track was secondaryContainer at 0.5 alpha. In Classic Light
    // that is #C2E7FF over #F8F9FA — effectively invisible. A neutral foreground
    // token with appearance-aware alpha keeps >=3:1 contrast in both schemes.
    val isLightAppearance = colors.background.luminance() > 0.5f
    val effectiveTrack = trackColor ?: colors.onSurfaceVariant.copy(
        alpha = if (isLightAppearance) 0.65f else 0.6f
    )

    val animatedProgress by animateFloatAsState(
        targetValue = if (indeterminate) 1f else value,
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "WavyProgressAnimation"
    )

    val percent = (value * 100).roundToInt()
    val thresholdPercent = (threshold * 100).roundToInt()
    val safetyState = if (isSafe) {
        "$percent% — at or above the $thresholdPercent% safety target"
    } else {
        "$percent% — below the $thresholdPercent% safety target"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .padding(horizontal = 4.dp)
            .semantics {
                // D-118: exactly three semantics are published here —
                // contentDescription (the caller's quantity-specific label),
                // stateDescription (percent + safety verdict, or "Loading") and,
                // for determinate values, progressBarRangeInfo. This Compose
                // artifact exposes no progress role constant, so none is set.
                contentDescription = label
                stateDescription = if (indeterminate) "Loading" else safetyState
                if (!indeterminate) {
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, 0)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokeWidthPx = 4.dp.toPx()
        val amplitude = 3.dp.toPx()
        val wavelength = 40.dp.toPx()
        val gap = 4.dp.toPx()

        val activeEnd = width * animatedProgress

        // 1. Draw Active Wavy Track
        if (activeEnd > 2.dp.toPx()) {
            val wavePath = Path()
            wavePath.moveTo(0f, centerY)

            var x = 0f
            while (x <= activeEnd) {
                val y = centerY + amplitude * sin((2 * PI * (x / wavelength)).toFloat())
                wavePath.lineTo(x, y)
                x += 2f
            }

            drawPath(
                path = wavePath,
                color = effectiveActive,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            )
        }

        // 2. Draw Remaining Flat Track (if not 100%)
        val remainingStart = (activeEnd + gap).coerceAtMost(width)
        val stopDotX = width - strokeWidthPx / 2f

        if (!indeterminate && remainingStart < stopDotX - gap) {
            drawLine(
                color = effectiveTrack,
                start = Offset(remainingStart, centerY),
                end = Offset(stopDotX - gap, centerY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            // 3. Draw 4dp Stop Dot
            drawCircle(
                color = effectiveActive,
                radius = strokeWidthPx / 2f,
                center = Offset(stopDotX, centerY)
            )
        }

        // 4. D-118: visible safety tick. A neutral, high-contrast notch marks the
        //    threshold so the safe/unsafe reading does not depend on colour alone.
        if (!indeterminate) {
            val tickX = width * threshold
            val tickColor = colors.onSurface.copy(alpha = if (isLightAppearance) 0.6f else 0.85f)
            drawLine(
                color = tickColor,
                start = Offset(tickX, centerY - amplitude - strokeWidthPx),
                end = Offset(tickX, centerY + amplitude + strokeWidthPx),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Preview
@Composable
private fun M3EWavyLinearProgressPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EWavyLinearProgress(progress = 0.75f, label = "Attendance")
    }
}
