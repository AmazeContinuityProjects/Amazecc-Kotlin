package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.ui.components.LocalAnimationsEnabled

val LocalM3EColors = staticCompositionLocalOf { M3EDarkPalette }

/**
 * D-114: the active palette preset, provided by [AmazeM3ETheme]. Nested theme
 * wrappers inherit it through their default parameter, so one root wiring makes
 * the whole tree consume the user's selection without per-screen edits.
 */
val LocalM3EPalettePreset = compositionLocalOf<M3EPalettePreset?> { null }

/**
 * D-114: set by every [AmazeM3ETheme] for its content. Only a theme composed
 * OUTSIDE any such provider (i.e. the real root) sees the default `false` and
 * publishes the non-composable motion fallback. Per-screen wrappers inherit
 * `true` and never write the global, so there is no last-writer-wins race.
 */
private val LocalMotionFallbackPublished = compositionLocalOf { false }

/**
 * D-114: composable face of the motion specs. Every `M3ETheme.motion.*` call
 * site is composable, so routing them here makes each spec read the current
 * [LocalMotionReduced] value instead of a global that a later composition could
 * overwrite. The plain [M3EMotion] factories remain for non-composable builders.
 */
object M3EMotionScope {
    @Composable
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = M3EMotion.spatialDefaultLocal()

    @Composable
    fun <T> spatialFast(): FiniteAnimationSpec<T> = M3EMotion.spatialFastLocal()

    @Composable
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = M3EMotion.spatialSlowLocal()

    @Composable
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = M3EMotion.effectsDefaultLocal()

    @Composable
    fun tabSwitchTransform(): ContentTransform = M3EMotion.tabSwitchTransformLocal()
}

object M3ETheme {
    val colors: M3EColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalM3EColors.current

    val motion = M3EMotionScope
}

@Composable
fun AmazeM3ETheme(
    isDark: Boolean = com.amazecc.app.shared.ui.screens.m3e.M3EStateBridge.isDarkThemeActive(),
    palettePreset: M3EPalettePreset? = LocalM3EPalettePreset.current,
    content: @Composable () -> Unit
) {
    val effectivePreset = palettePreset ?: M3EPalettePreset.OCEAN_MIDNIGHT
    val palette = resolveM3EPalette(isDark, effectivePreset)

    // D-114: composable spec providers read LocalMotionReduced directly. The one
    // remaining global is an opt-in fallback for plain (non-composable) transition
    // builders. It is published ONLY by the theme composed outside any parent
    // AmazeM3ETheme provider (LocalMotionFallbackPublished == false), via
    // SideEffect. Nested per-screen wrappers read `true` from the provider and do
    // not write it — no last-writer-wins race, and no write from a child theme.
    val reducedMotion = platformPrefersReducedMotion() || !LocalAnimationsEnabled.current
    if (!LocalMotionFallbackPublished.current) {
        SideEffect { M3EMotion.fallbackReducedMotion = reducedMotion }
    }

    val m3Colors = if (isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.tertiary,
            onTertiary = palette.onTertiary,
            tertiaryContainer = palette.tertiaryContainer,
            onTertiaryContainer = palette.onTertiaryContainer,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceContainerHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            background = palette.background,
            onBackground = palette.onBackground,
            error = palette.error,
            onError = palette.onError,
            errorContainer = palette.errorContainer,
            onErrorContainer = palette.onErrorContainer,
            outline = palette.outline,
            outlineVariant = palette.outlineVariant
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.tertiary,
            onTertiary = palette.onTertiary,
            tertiaryContainer = palette.tertiaryContainer,
            onTertiaryContainer = palette.onTertiaryContainer,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceContainerHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            background = palette.background,
            onBackground = palette.onBackground,
            error = palette.error,
            onError = palette.onError,
            errorContainer = palette.errorContainer,
            onErrorContainer = palette.onErrorContainer,
            outline = palette.outline,
            outlineVariant = palette.outlineVariant
        )
    }

    CompositionLocalProvider(
        LocalM3EColors provides palette,
        LocalM3EPalettePreset provides effectivePreset,
        LocalMotionReduced provides reducedMotion,
        LocalMotionFallbackPublished provides true,
        LocalContentColor provides palette.onBackground
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            content = content
        )
    }
}
