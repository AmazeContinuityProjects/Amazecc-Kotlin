package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable

/**
 * Material 3 Expressive (M3E) Spring Physics & Motion Specifications.
 * Replaces fixed-duration easing curves with lively physics-based springs.
 *
 * D-114: the effective reduce-motion decision is no longer pushed into a global
 * on every composition (last-writer-wins across the many per-screen theme
 * wrappers). Composable call sites go through [M3ETheme.motion], whose spec
 * providers read [LocalMotionReduced] via the `*Local` helpers below. Plain
 * transition builders keep calling these no-arg factories unchanged; they use
 * [fallbackReducedMotion] as an explicit, one-time root fallback.
 *
 * A reduced spec is a true instant [snap] with no travel — never a stiff spring
 * that still animates for a few frames.
 */
object M3EMotion {

    /**
     * Fallback for NON-composable call sites only (e.g. `onboardingPageTransition`).
     * Set this once at the app root if those transitions must also honour the
     * preference; never write it from a per-screen composition. Composable spec
     * providers ignore it and read [LocalMotionReduced] instead.
     */
    @Volatile
    var fallbackReducedMotion: Boolean = false

    /** Source-compatible alias; no longer written by [AmazeM3ETheme]. */
    @Deprecated(
        "Reduced motion is read from LocalMotionReduced in composables; " +
            "set fallbackReducedMotion once at the root for non-composable call sites."
    )
    var reducedMotionEnabled: Boolean
        get() = fallbackReducedMotion
        set(value) { fallbackReducedMotion = value }

    /** An instant, non-animated spec — no travel, no overshoot. */
    private fun <T> instant(): FiniteAnimationSpec<T> = snap()

    private fun <T> spatialDefaultFor(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) instant() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, // ~0.75-0.85
            stiffness = Spring.StiffnessMedium              // ~700-1000
        )

    private fun <T> spatialFastFor(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) instant() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        )

    private fun <T> spatialSlowFor(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) instant() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )

    private fun <T> effectsDefaultFor(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) instant() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy, // 1.0 (critically damped)
            stiffness = Spring.StiffnessMedium
        )

    private fun tabSwitchTransformFor(reduced: Boolean): ContentTransform {
        val duration = if (reduced) 0 else M3EFeedbackDurations.TAB_TRANSITION_MS
        return (fadeIn(animationSpec = tween(duration)) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(duration)
        )) togetherWith (fadeOut(animationSpec = tween(duration)) + scaleOut(
            targetScale = 0.98f,
            animationSpec = tween(duration)
        ))
    }

    // ---- Non-composable factories (plain transition builders) ----------------
    // Original no-arg signatures are preserved; `onboardingPageTransition` and
    // other plain call sites keep compiling without edits.

    // Expressive Spatial Spring: Bouncy, lively overshoot for cards, buttons, dialogs
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spatialDefaultFor(fallbackReducedMotion)

    // Expressive Spatial Fast: Snappy micro-interactions (switches, icon scale)
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spatialFastFor(fallbackReducedMotion)

    // Spatial Slow: Hero screen transitions, sheet expansions
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spatialSlowFor(fallbackReducedMotion)

    // Effects Spring: Smooth color/alpha fading without overshoot
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = effectsDefaultFor(fallbackReducedMotion)

    /**
     * D-089: ready-made `AnimatedContent` transform for tab-to-tab switches —
     * a real crossfade + 0.98 scale instead of `tween(0)`. Duration comes from
     * [M3EFeedbackDurations] and collapses to 0 under reduced motion (D-084).
     */
    fun tabSwitchTransform(): ContentTransform = tabSwitchTransformFor(fallbackReducedMotion)

    // ---- Composable spec providers (read the per-composition decision) -------
    // Distinct names avoid composable/non-composable overload ambiguity. These
    // back M3ETheme.motion, so composable call sites follow LocalMotionReduced.

    @Composable
    fun <T> spatialDefaultLocal(): FiniteAnimationSpec<T> =
        spatialDefaultFor(LocalMotionReduced.current)

    @Composable
    fun <T> spatialFastLocal(): FiniteAnimationSpec<T> =
        spatialFastFor(LocalMotionReduced.current)

    @Composable
    fun <T> spatialSlowLocal(): FiniteAnimationSpec<T> =
        spatialSlowFor(LocalMotionReduced.current)

    @Composable
    fun <T> effectsDefaultLocal(): FiniteAnimationSpec<T> =
        effectsDefaultFor(LocalMotionReduced.current)

    @Composable
    fun tabSwitchTransformLocal(): ContentTransform =
        tabSwitchTransformFor(LocalMotionReduced.current)
}
