package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (isDark) M3EDarkPalette else M3ELightPalette

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
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceContainerHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            background = palette.background,
            onBackground = palette.onBackground,
            error = palette.error,
            onError = palette.onError
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
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceContainerHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            background = palette.background,
            onBackground = palette.onBackground,
            error = palette.error,
            onError = palette.onError
        )
    }

    CompositionLocalProvider(
        LocalM3EColors provides palette
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            content = content
        )
    }
}
