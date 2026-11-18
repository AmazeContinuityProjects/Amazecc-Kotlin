package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive (M3E) Spring Physics & Motion Specifications.
 * Replaces fixed-duration easing curves with lively physics-based springs.
 */
object M3EMotion {

    // Expressive Spatial Spring: Bouncy, lively overshoot for cards, buttons, dialogs
    fun <T> spatialDefault(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy, // ~0.75-0.85
        stiffness = Spring.StiffnessMedium              // ~700-1000
    )

    // Expressive Spatial Fast: Snappy micro-interactions (switches, icon scale)
    fun <T> spatialFast(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // Spatial Slow: Hero screen transitions, sheet expansions
    fun <T> spatialSlow(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    // Effects Spring: Smooth color/alpha fading without overshoot
    fun <T> effectsDefault(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0 (critically damped)
        stiffness = Spring.StiffnessMedium
    )
}
