package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

/**
 * Material 3 Expressive (M3E) Spring Physics & Motion Specifications.
 * Replaces fixed-duration easing curves with lively physics-based springs.
 *
 * D-084: [reducedMotionEnabled] is the effective OS + in-app decision published
 * by [AmazeM3ETheme]. The spec factory functions stay non-composable so every
 * existing call site (including non-composable transition builders such as
 * `onboardingPageTransition`) keeps compiling unchanged, while every spec now
 * collapses to a critically damped, near-instant spring when motion is reduced.
 */
object M3EMotion {

    /** Set by [AmazeM3ETheme] from [LocalMotionReduced] on every composition. */
    @Volatile
    var reducedMotionEnabled: Boolean = false

    private fun <T> instant(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        // This Compose version exposes StiffnessLow..StiffnessHigh only; High is
        // the fastest critically damped spring available (no overshoot/travel).
        stiffness = Spring.StiffnessHigh
    )

    // Expressive Spatial Spring: Bouncy, lively overshoot for cards, buttons, dialogs
    fun <T> spatialDefault(): SpringSpec<T> = if (reducedMotionEnabled) {
        instant()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, // ~0.75-0.85
            stiffness = Spring.StiffnessMedium              // ~700-1000
        )
    }

    // Expressive Spatial Fast: Snappy micro-interactions (switches, icon scale)
    fun <T> spatialFast(): SpringSpec<T> = if (reducedMotionEnabled) {
        instant()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        )
    }

    // Spatial Slow: Hero screen transitions, sheet expansions
    fun <T> spatialSlow(): SpringSpec<T> = if (reducedMotionEnabled) {
        instant()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    }

    // Effects Spring: Smooth color/alpha fading without overshoot
    fun <T> effectsDefault(): SpringSpec<T> = if (reducedMotionEnabled) {
        instant()
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy, // 1.0 (critically damped)
            stiffness = Spring.StiffnessMedium
        )
    }

    /**
     * D-089: ready-made `AnimatedContent` transform for tab-to-tab switches —
     * a real crossfade + 0.98 scale instead of `tween(0)`. Duration comes from
     * [M3EFeedbackDurations] and collapses to 0 under reduced motion (D-084).
     */
    fun tabSwitchTransform(): ContentTransform {
        val duration = if (reducedMotionEnabled) 0 else M3EFeedbackDurations.TAB_TRANSITION_MS
        return (fadeIn(animationSpec = tween(duration)) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(duration)
        )) togetherWith (fadeOut(animationSpec = tween(duration)) + scaleOut(
            targetScale = 0.98f,
            animationSpec = tween(duration)
        ))
    }
}
