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
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material 3 Expressive Wavy Progress Indicator (The signature M3E visual).
 * Sourced directly from M3E specification §9.1:
 * - 3dp amplitude sine wave
 * - 40dp wavelength
 * - 4dp stroke thickness
 * - 4dp gap between active wave and flat track
 * - 4dp stop dot at end of track
 */
@Composable
fun M3EWavyLinearProgress(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    activeColor: Color? = null,
    trackColor: Color? = null
) {
    val colors = M3ETheme.colors
    val effectiveActive = activeColor ?: colors.primary
    val effectiveTrack = trackColor ?: colors.secondaryContainer.copy(alpha = 0.5f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "WavyProgressAnimation"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .padding(horizontal = 4.dp)
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

        if (remainingStart < stopDotX - gap) {
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
    }
}
