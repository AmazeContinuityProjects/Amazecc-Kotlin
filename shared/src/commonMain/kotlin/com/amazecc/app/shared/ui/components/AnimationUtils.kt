package com.amazecc.app.shared.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High bouncy spring spec for juicy press and tap interactions.
 */
fun <T> bouncySpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioHighBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * Smooth medium bouncy spring spec for tab switches and expansions.
 */
fun <T> mediumSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

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
