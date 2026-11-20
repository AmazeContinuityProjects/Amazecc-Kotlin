package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.amazecc.app.shared.ui.components.LocalAnimationsEnabled

val LocalM3EColors = staticCompositionLocalOf { M3EDarkPalette }

object M3ETheme {
    val colors: M3EColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalM3EColors.current

    val motion = M3EMotion
}

@Composable
fun AmazeM3ETheme(
    isDark: Boolean = com.amazecc.app.shared.ui.screens.m3e.M3EStateBridge.isDarkThemeActive(),
    content: @Composable () -> Unit
) {
    val palette = if (isDark) M3EDarkPalette else M3ELightPalette

    // D-084: OS "reduce motion" plus the existing in-app Animations toggle are
    // folded into one effective decision for every M3E motion spec. The global
    // publication keeps M3EMotion's factory functions non-composable (some call
    // sites are plain transition builders); LocalMotionReduced remains the
    // composable source for primitives that read it directly, e.g. skeletons.
    val reducedMotion = platformPrefersReducedMotion() || !LocalAnimationsEnabled.current
    M3EMotion.reducedMotionEnabled = reducedMotion

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
            outline = palette.outline,
            outlineVariant = palette.outlineVariant
        )
    }

    CompositionLocalProvider(
        LocalM3EColors provides palette,
        LocalMotionReduced provides reducedMotion,
        LocalContentColor provides palette.onBackground
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            content = content
        )
    }
}
