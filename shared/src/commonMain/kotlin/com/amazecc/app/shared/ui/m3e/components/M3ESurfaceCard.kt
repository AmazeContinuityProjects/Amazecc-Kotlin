package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class SurfaceContainerLevel {
    LOWEST, LOW, STANDARD, HIGH, HIGHEST
}

@Composable
fun M3ESurfaceCard(
    modifier: Modifier = Modifier,
    containerLevel: SurfaceContainerLevel = SurfaceContainerLevel.STANDARD,
    cornerRadius: Dp = 20.dp, // Large increased token
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = M3ETheme.colors
    val containerColor = when (containerLevel) {
        SurfaceContainerLevel.LOWEST -> colors.surfaceContainerLowest
        SurfaceContainerLevel.LOW -> colors.surfaceContainerLow
        SurfaceContainerLevel.STANDARD -> colors.surfaceContainer
        SurfaceContainerLevel.HIGH -> colors.surfaceContainerHigh
        SurfaceContainerLevel.HIGHEST -> colors.surfaceContainerHighest
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1.0f,
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "M3ECardPressMorph"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val effectiveBorderColor = borderColor ?: colors.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .border(1.dp, effectiveBorderColor, shape)
            .then(
                if (onClick != null) {
                    // D-085: explicit button role + focus + Enter/Space activation.
                    Modifier.clickableA11y(onClick = onClick)
                } else Modifier
            )
            .padding(16.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            content()
        }
    }
}

@Preview
@Composable
private fun M3ESurfaceCardPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ESurfaceCard {
            Text("M3E Expressive Surface Card")
        }
    }
}
