@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

enum class AccentTheme {
    OCEAN, FOREST, LAVENDER, SUNSET
}

@Stable
class AmazeColors(
    background: Color,
    surface: Color,
    elevatedSurface: Color,
    border: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    accent: Color,
    success: Color,
    successSurface: Color,
    successText: Color,
    warning: Color,
    warningSurface: Color,
    warningText: Color,
    danger: Color,
    dangerSurface: Color,
    dangerText: Color,
    info: Color,
    infoSurface: Color,
    infoText: Color,
    navBackground: Color,
    navBorder: Color,
    glassSurface: Color,
    glassBorder: Color
) {
    var background by mutableStateOf(background)
        private set
    var surface by mutableStateOf(surface)
        private set
    var elevatedSurface by mutableStateOf(elevatedSurface)
        private set
    var border by mutableStateOf(border)
        private set
    var textPrimary by mutableStateOf(textPrimary)
        private set
    var textSecondary by mutableStateOf(textSecondary)
        private set
    var textMuted by mutableStateOf(textMuted)
        private set
    var accent by mutableStateOf(accent)
        private set
    var success by mutableStateOf(success)
        private set
    var successSurface by mutableStateOf(successSurface)
        private set
    var successText by mutableStateOf(successText)
        private set
    var warning by mutableStateOf(warning)
        private set
    var warningSurface by mutableStateOf(warningSurface)
        private set
    var warningText by mutableStateOf(warningText)
        private set
    var danger by mutableStateOf(danger)
        private set
    var dangerSurface by mutableStateOf(dangerSurface)
        private set
    var dangerText by mutableStateOf(dangerText)
        private set
    var info by mutableStateOf(info)
        private set
    var infoSurface by mutableStateOf(infoSurface)
        private set
    var infoText by mutableStateOf(infoText)
        private set
    var navBackground by mutableStateOf(navBackground)
        private set
    var navBorder by mutableStateOf(navBorder)
        private set
    var glassSurface by mutableStateOf(glassSurface)
        private set
    var glassBorder by mutableStateOf(glassBorder)
        private set

    fun copy(
        background: Color = this.background,
        surface: Color = this.surface,
        elevatedSurface: Color = this.elevatedSurface,
        border: Color = this.border,
        textPrimary: Color = this.textPrimary,
        textSecondary: Color = this.textSecondary,
        textMuted: Color = this.textMuted,
        accent: Color = this.accent,
        success: Color = this.success,
        successSurface: Color = this.successSurface,
        successText: Color = this.successText,
        warning: Color = this.warning,
        warningSurface: Color = this.warningSurface,
        warningText: Color = this.warningText,
        danger: Color = this.danger,
        dangerSurface: Color = this.dangerSurface,
        dangerText: Color = this.dangerText,
        info: Color = this.info,
        infoSurface: Color = this.infoSurface,
        infoText: Color = this.infoText,
        navBackground: Color = this.navBackground,
        navBorder: Color = this.navBorder,
        glassSurface: Color = this.glassSurface,
        glassBorder: Color = this.glassBorder
    ) = AmazeColors(
        background, surface, elevatedSurface, border, textPrimary, textSecondary, textMuted, accent,
        success, successSurface, successText, warning, warningSurface, warningText, danger, dangerSurface, dangerText, info, infoSurface, infoText,
        navBackground, navBorder, glassSurface, glassBorder
    )

    fun updateWith(other: AmazeColors) {
        background = other.background
        surface = other.surface
        elevatedSurface = other.elevatedSurface
        border = other.border
        textPrimary = other.textPrimary
        textSecondary = other.textSecondary
        textMuted = other.textMuted
        accent = other.accent
        success = other.success
        successSurface = other.successSurface
        successText = other.successText
        warning = other.warning
        warningSurface = other.warningSurface
        warningText = other.warningText
        danger = other.danger
        dangerSurface = other.dangerSurface
        dangerText = other.dangerText
        info = other.info
        infoSurface = other.infoSurface
        infoText = other.infoText
        navBackground = other.navBackground
        navBorder = other.navBorder
        glassSurface = other.glassSurface
        glassBorder = other.glassBorder
    }
}

@Stable
class AmazeRadius(
    val small: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)

val LocalAmazeColors = staticCompositionLocalOf<AmazeColors> {
    error("No AmazeColors provided")
}

val LocalAmazeRadius = staticCompositionLocalOf { AmazeRadius() }
val LocalAmazeTypography = staticCompositionLocalOf { 
    AmazeTypography(
        androidx.compose.ui.text.TextStyle(),
        androidx.compose.ui.text.TextStyle(),
        androidx.compose.ui.text.TextStyle(),
        androidx.compose.ui.text.TextStyle(),
        androidx.compose.ui.text.TextStyle(),
        androidx.compose.ui.text.TextStyle()
    ) 
}

@Composable
fun AmazeTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    accentTheme: AccentTheme = AccentTheme.OCEAN,
    content: @Composable () -> Unit
) {
    val accent = when (accentTheme) {
        AccentTheme.OCEAN -> AccentOcean
        AccentTheme.FOREST -> AccentForest
        AccentTheme.LAVENDER -> AccentLavender
        AccentTheme.SUNSET -> AccentSunset
    }

    val resolvedTheme = when (appTheme) {
        AppTheme.SYSTEM -> if (isSystemInDarkTheme()) AppTheme.DARK else AppTheme.LIGHT
        else -> appTheme
    }

    val colors = when (resolvedTheme) {
        AppTheme.LIGHT -> AmazeColors(
            background = NeutralBgLight,
            surface = NeutralSurfaceLight,
            elevatedSurface = NeutralElevatedLight,
            border = NeutralBorderLight,
            textPrimary = NeutralTextPrimaryLight,
            textSecondary = NeutralTextSecondaryLight,
            textMuted = NeutralTextMutedLight,
            accent = accent,
            success = ColorSuccess,
            successSurface = ColorSuccessSurfaceLight,
            successText = ColorSuccessTextLight,
            warning = ColorWarning,
            warningSurface = ColorWarningSurfaceLight,
            warningText = ColorWarningTextLight,
            danger = ColorDanger,
            dangerSurface = ColorDangerSurfaceLight,
            dangerText = ColorDangerTextLight,
            info = ColorInfo,
            infoSurface = ColorInfoSurfaceLight,
            infoText = ColorInfoTextLight,
            navBackground = NavBgLight,
            navBorder = NavBorderLight,
            glassSurface = GlassSurfaceLight,
            glassBorder = GlassBorderLight
        )
        AppTheme.DARK -> AmazeColors(
            background = NeutralBgDark,
            surface = NeutralSurfaceDark,
            elevatedSurface = NeutralElevatedDark,
            border = NeutralBorderDark,
            textPrimary = NeutralTextPrimaryDark,
            textSecondary = NeutralTextSecondaryDark,
            textMuted = NeutralTextMutedDark,
            accent = accent,
            success = ColorSuccess,
            successSurface = ColorSuccessSurfaceDark,
            successText = ColorSuccessTextDark,
            warning = ColorWarning,
            warningSurface = ColorWarningSurfaceDark,
            warningText = ColorWarningTextDark,
            danger = ColorDanger,
            dangerSurface = ColorDangerSurfaceDark,
            dangerText = ColorDangerTextDark,
            info = ColorInfo,
            infoSurface = ColorInfoSurfaceDark,
            infoText = ColorInfoTextDark,
            navBackground = NavBgDark,
            navBorder = NavBorderDark,
            glassSurface = GlassSurfaceDark,
            glassBorder = GlassBorderDark
        )
        AppTheme.SYSTEM -> error("System theme must be resolved before selecting colors")
    }

    val rememberColors = remember { colors }.apply { updateWith(colors) }
    val radius = remember { AmazeRadius() }
    val typography = getAmazeTypography()

    CompositionLocalProvider(
        LocalAmazeColors provides rememberColors,
        LocalAmazeRadius provides radius,
        LocalAmazeTypography provides typography,
        content = content
    )
}

object AmazeTheme {
    val colors: AmazeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeColors.current

    val radius: AmazeRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeRadius.current

    val typography: AmazeTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeTypography.current
}
