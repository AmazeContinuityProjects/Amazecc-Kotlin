package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.LocalMotionReduced
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive (M3E) 7-Shape Morphing Loading Indicator (§9.2 Specification).
 *
 * Sourced directly from the official M3E specification:
 * In M3E, the loading indicator is NOT a spinning circular arc. It is a single organic
 * shape that continuously and smoothly morphs through 7 Material 3 shapes on an infinite loop:
 *
 * 1. Soft Burst (8-lobed organic star)
 * 2. 9-Sided Cookie (scalloped polygon)
 * 3. Pentagon (5-sided geometric shape)
 * 4. Pill (stadium/capsule shape)
 * 5. Sunny (12-lobed burst)
 * 6. 4-Sided Cookie (softened squircle cookie)
 * 7. Oval (curved oblong)
 *
 * Accelerates mid-morph, continuously rotates, and respects system reduce-motion settings.
 *
 * @param modifier Modifier applied to the canvas container
 * @param size Target diameter of the loading blob (default 48dp, standard component size)
 * @param color Active fill color (defaults to [M3ETheme.colors.primary])
 */
@Composable
fun M3ELoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color? = null
) {
    val activeColor = color ?: M3ETheme.colors.primary
    val reducedMotion = LocalMotionReduced.current

    val infiniteTransition = rememberInfiniteTransition(label = "M3ELoaderTransition")

    // Continuous rotation with mid-morph acceleration
    val rotation by if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "M3ELoaderRotation"
        )
    }

    // Morph phase progress (0f..7f) through the 7 Material shapes
    val morphProgress by if (reducedMotion) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "M3ELoaderMorphPhase"
        )
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .semantics {
                contentDescription = "Loading"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
    ) {
        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val radius = size.toPx() * 0.42f

        val phase = morphProgress % 7f
        val currentShape = phase.toInt()
        val blend = phase - currentShape

        val path = Path().apply {
            val numPoints = 72
            for (i in 0 until numPoints) {
                val angle = (2 * PI * i / numPoints).toFloat()
                val r = calculateM3EShapeRadius(currentShape, (currentShape + 1) % 7, blend, angle, radius)
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        drawPath(path, activeColor)
    }
}

/**
 * Calculates the distance from center at a given [angle] by blending between [shapeA] and [shapeB].
 */
private fun calculateM3EShapeRadius(
    shapeA: Int,
    shapeB: Int,
    blend: Float,
    angle: Float,
    baseR: Float
): Float {
    fun radiusForShape(shape: Int): Float = when (shape) {
        0 -> baseR * (1f + 0.18f * cos(8 * angle))   // 1. Soft burst (8-lobe)
        1 -> baseR * (1f + 0.12f * cos(9 * angle))   // 2. 9-sided cookie
        2 -> baseR * (1f + 0.15f * cos(5 * angle))   // 3. Pentagon
        3 -> baseR * (1f + 0.22f * abs(cos(angle)))  // 4. Pill
        4 -> baseR * (1f + 0.20f * cos(12 * angle))  // 5. Sunny (12-lobe)
        5 -> baseR * (1f + 0.18f * cos(4 * angle))   // 6. 4-sided cookie
        else -> baseR * (1f + 0.25f * cos(2 * angle)) // 7. Oval
    }
    return radiusForShape(shapeA) * (1f - blend) + radiusForShape(shapeB) * blend
}

@Preview
@Composable
private fun M3ELoadingIndicatorPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ELoadingIndicator(size = 48.dp)
    }
}
