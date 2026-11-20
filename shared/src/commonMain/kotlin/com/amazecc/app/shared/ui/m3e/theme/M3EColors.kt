package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.ui.graphics.Color
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.theme.AccentTheme

/**
 * Material 3 Expressive (M3E) Tonal Surface & Accent Palette for AmazeCC.
 * Built directly from the official M3E specification token tables.
 *
 * D-114: every `on*` status token is a *measured* WCAG pairing (sRGB relative
 * luminance), with the ratio documented inline. Text colors are never guessed
 * from the fill — a container always gets its own `on*Container` token so a
 * component can stop inventing one.
 */
data class M3EColorPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,

    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,

    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,

    // 5-Tier Surface Container Depth
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,

    val outline: Color,
    val outlineVariant: Color,

    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color
)

// Pitch-Black Midnight OLED Scheme (AmazeCC signature)
val M3EDarkPalette = M3EColorPalette(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),

    secondary = Color(0xFF7FCFFF),
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF004C70),
    onSecondaryContainer = Color(0xFFC2E7FF),

    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),

    background = Color(0xFF000000),      // Pure Pitch Black
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF0B0B0E),
    onSurface = Color(0xFFE3E2E6),
    onSurfaceVariant = Color(0xFFC4C6D0),

    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F1014),
    surfaceContainer = Color(0xFF16171D),
    surfaceContainerHigh = Color(0xFF1E2028),
    surfaceContainerHighest = Color(0xFF282B36),

    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF33353E),

    success = Color(0xFF6DD58C),
    onSuccess = Color(0xFF003919),          // 7.22:1 on success
    successContainer = Color(0xFF0F5228),
    onSuccessContainer = Color(0xFFB9F0C8), // 7.25:1 on success container

    warning = Color(0xFFFFBA28),
    onWarning = Color(0xFF422C00),          // 7.73:1 on warning
    warningContainer = Color(0xFF5F4100),
    onWarningContainer = Color(0xFFFFE1A6), // 7.39:1 on warning container

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),            // 7.72:1 on error
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)    // 7.24:1 on error container
)

val M3ELightPalette = M3EColorPalette(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),

    secondary = Color(0xFF00639B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D32),

    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),

    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF444746),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F6),
    surfaceContainer = Color(0xFFEDEEF2),
    surfaceContainerHigh = Color(0xFFE7E8EC),
    surfaceContainerHighest = Color(0xFFE1E3E8),

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C7C5),

    // D-114: the old fills paired with white were 4.21:1 (success) and 3.10:1
    // (warning) — both below AA. Mint keeps its semantic hue but uses a darker,
    // measured fill so white text is now 6.53:1. Amber keeps its documented fill
    // and gains a measured dark on-color (5.28:1) instead of white.
    success = Color(0xFF146C2E),
    onSuccess = Color(0xFFFFFFFF),          // 6.53:1 on success
    successContainer = Color(0xFFCEEAD6),
    onSuccessContainer = Color(0xFF0A3D1B), // 9.65:1 on success container

    warning = Color(0xFFE37400),
    onWarning = Color(0xFF2E1C00),          // 5.28:1 on warning
    warningContainer = Color(0xFFFEEFC3),
    onWarningContainer = Color(0xFF422C00), // 11.53:1 on warning container

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),            // 6.46:1 on error
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)    // 13.26:1 on error container
)

// ---------------------------------------------------------------------------
// D-114: palette presets
// ---------------------------------------------------------------------------

/** Accent roles for one preset in one appearance; all pairs are AA measured. */
private data class M3EAccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color
)

private val M3EDarkAccents = mapOf(
    M3EPalettePreset.OCEAN_MIDNIGHT to M3EAccentRoles(
        M3EDarkPalette.primary, M3EDarkPalette.onPrimary,
        M3EDarkPalette.primaryContainer, M3EDarkPalette.onPrimaryContainer
    ),
    M3EPalettePreset.CRIMSON_VELVET to M3EAccentRoles(
        Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000A), Color(0xFFFFDAD6)
    ), // 7.72:1 / 7.24:1
    M3EPalettePreset.EMERALD_MINT to M3EAccentRoles(
        Color(0xFF6DD58C), Color(0xFF003919), Color(0xFF0F5228), Color(0xFFB9F0C8)
    ), // 7.22:1 / 7.25:1
    M3EPalettePreset.AMBER_SUNSET to M3EAccentRoles(
        Color(0xFFFFBA28), Color(0xFF422C00), Color(0xFF5F4100), Color(0xFFFFE1A6)
    ), // 7.73:1 / 7.39:1
    M3EPalettePreset.PURPLE_DUSK to M3EAccentRoles(
        Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF)
    )  // 7.71:1 / 7.23:1
)

private val M3ELightAccents = mapOf(
    M3EPalettePreset.OCEAN_MIDNIGHT to M3EAccentRoles(
        M3ELightPalette.primary, M3ELightPalette.onPrimary,
        M3ELightPalette.primaryContainer, M3ELightPalette.onPrimaryContainer
    ),
    M3EPalettePreset.CRIMSON_VELVET to M3EAccentRoles(
        Color(0xFFB3261E), Color(0xFFFFFFFF), Color(0xFFFFDAD6), Color(0xFF410002)
    ), // 6.54:1 / 13.26:1
    M3EPalettePreset.EMERALD_MINT to M3EAccentRoles(
        Color(0xFF146C2E), Color(0xFFFFFFFF), Color(0xFFC8EAD0), Color(0xFF00250F)
    ), // 6.53:1 / 12.69:1
    M3EPalettePreset.AMBER_SUNSET to M3EAccentRoles(
        Color(0xFF8A5000), Color(0xFFFFFFFF), Color(0xFFFFDDAB), Color(0xFF2D1600)
    ), // 6.51:1 / 13.20:1
    M3EPalettePreset.PURPLE_DUSK to M3EAccentRoles(
        Color(0xFF6750A4), Color(0xFFFFFFFF), Color(0xFFEADDFF), Color(0xFF21005D)
    )  // 6.44:1 / 13.32:1
)

/**
 * D-114: derive a preset by recoloring only the accent roles. The tonal canvas,
 * status tokens and outlines are inherited from the appearance so a preset can
 * never weaken a measured status on-color or tint the pitch-black canvas.
 */
internal fun M3EColorPalette.withAccentPreset(
    preset: M3EPalettePreset,
    isDark: Boolean
): M3EColorPalette {
    val roles = (if (isDark) M3EDarkAccents else M3ELightAccents)[preset] ?: return this
    return copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer
    )
}

/** Resolve the full palette for an appearance and an optional user preset. */
internal fun resolveM3EPalette(
    isDark: Boolean,
    preset: M3EPalettePreset?
): M3EColorPalette {
    val base = if (isDark) M3EDarkPalette else M3ELightPalette
    return if (preset == null || preset == M3EPalettePreset.OCEAN_MIDNIGHT) {
        base
    } else {
        base.withAccentPreset(preset, isDark)
    }
}

/**
 * D-114: [AppState.accent][com.amazecc.app.shared.state.AppState.accent] is an
 * [AccentTheme]; this maps it to the M3E preset so the root host can wire the
 * theme in one line. Same mapping the Settings host already uses.
 */
fun palettePresetForAccent(accent: AccentTheme): M3EPalettePreset = when (accent) {
    AccentTheme.OCEAN -> M3EPalettePreset.OCEAN_MIDNIGHT
    AccentTheme.FOREST, AccentTheme.VERDANT -> M3EPalettePreset.EMERALD_MINT
    AccentTheme.LAVENDER -> M3EPalettePreset.PURPLE_DUSK
    AccentTheme.SUNSET -> M3EPalettePreset.AMBER_SUNSET
    AccentTheme.CUSTOM -> M3EPalettePreset.CRIMSON_VELVET
}
