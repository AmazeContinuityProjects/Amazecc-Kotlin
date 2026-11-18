package com.amazecc.app.shared.ui.m3e.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 Expressive (M3E) Tonal Surface & Accent Palette for AmazeCC.
 * Built directly from the official M3E specification token tables.
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

    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,

    val error: Color,
    val onError: Color,
    val errorContainer: Color
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
    onSuccess = Color(0xFF003919),
    successContainer = Color(0xFF0F5228),

    warning = Color(0xFFFFBA28),
    onWarning = Color(0xFF422C00),
    warningContainer = Color(0xFF5F4100),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A)
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

    success = Color(0xFF1E8E3E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFCEEAD6),

    warning = Color(0xFFE37400),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEEFC3),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6)
)
