package com.amazecc.app.shared.ui.m3e.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/**
 * Material 3 Expressive (M3E) Accordion Chevron.
 *
 * Continuously animates a single [Icons.Rounded.ExpandMore] arrow from 0° (collapsed, pointing down)
 * to 180° (expanded, pointing up) using [M3ETheme.motion.spatialDefault] physics-based spring.
 *
 * Avoids the double-inversion bug caused by simultaneously swapping icon vectors and rotating.
 */
@Composable
fun M3EAccordionChevron(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = if (isExpanded) "Collapse" else "Expand",
    tint: Color? = null
) {
    val rotation by rememberM3EAccordionRotation(isExpanded = isExpanded)
    Icon(
        imageVector = Icons.Rounded.ExpandMore,
        contentDescription = contentDescription,
        tint = tint ?: M3ETheme.colors.onSurfaceVariant,
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    )
}

/**
 * Returns an animated rotation float [0f..180f] driven by M3E spatial spring physics.
 */
@Composable
fun rememberM3EAccordionRotation(isExpanded: Boolean): State<Float> {
    return animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = M3ETheme.motion.spatialDefault(),
        label = "M3EAccordionChevronRotation"
    )
}

/**
 * Material 3 Expressive (M3E) Accordion Card.
 *
 * Adheres to M3 Expressive guidelines:
 * 1. **Container Tier Shift**: Transitions from [collapsedLevel] (default [SurfaceContainerLevel.LOW])
 *    to [expandedLevel] (default [SurfaceContainerLevel.HIGH]) when expanded, providing clear depth hierarchy.
 * 2. **Spatial Spring Height Expansion**: Content expansion and collapse is driven by
 *    [expandVertically] with [M3ETheme.motion.spatialDefault] and [fadeIn] with [M3ETheme.motion.effectsDefault].
 * 3. **Interactive Tactile Feedback**: Integrates tactile press scaling and accessible actions via [M3ESurfaceCard].
 */
@Composable
fun M3EAccordionCard(
    isExpanded: Boolean,
    onToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    collapsedLevel: SurfaceContainerLevel = SurfaceContainerLevel.LOW,
    expandedLevel: SurfaceContainerLevel = SurfaceContainerLevel.HIGH,
    cornerRadius: Dp = 20.dp,
    borderColor: Color? = null,
    header: @Composable ColumnScope.(isExpanded: Boolean) -> Unit,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    M3ESurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerLevel = if (isExpanded) expandedLevel else collapsedLevel,
        cornerRadius = cornerRadius,
        onClick = onToggle,
        borderColor = borderColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            header(isExpanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = M3ETheme.motion.spatialDefault()) +
                    fadeIn(animationSpec = M3ETheme.motion.effectsDefault()),
                exit = shrinkVertically(animationSpec = M3ETheme.motion.spatialDefault()) +
                    fadeOut(animationSpec = M3ETheme.motion.effectsDefault())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    expandedContent()
                }
            }
        }
    }
}
