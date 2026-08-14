package com.amazecc.app.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppTheme {
    LIGHT, DARK, AMOLED, SYSTEM
}

enum class AccentTheme {
    OCEAN, FOREST, LAVENDER, SUNSET, CUSTOM
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
    accentSurface: Color,
    accentContainer: Color,
    onAccent: Color,
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
    chart1: Color,
    chart2: Color,
    chart3: Color,
    chart4: Color,
    chart5: Color,
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
    var accentSurface by mutableStateOf(accentSurface)
        private set
    var accentContainer by mutableStateOf(accentContainer)
        private set
    var onAccent by mutableStateOf(onAccent)
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
    var chart1 by mutableStateOf(chart1)
        private set
    var chart2 by mutableStateOf(chart2)
        private set
    var chart3 by mutableStateOf(chart3)
        private set
    var chart4 by mutableStateOf(chart4)
        private set
    var chart5 by mutableStateOf(chart5)
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
        accentSurface: Color = this.accentSurface,
        accentContainer: Color = this.accentContainer,
        onAccent: Color = this.onAccent,
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
        chart1: Color = this.chart1,
        chart2: Color = this.chart2,
        chart3: Color = this.chart3,
        chart4: Color = this.chart4,
        chart5: Color = this.chart5,
        navBackground: Color = this.navBackground,
        navBorder: Color = this.navBorder,
        glassSurface: Color = this.glassSurface,
        glassBorder: Color = this.glassBorder
    ) = AmazeColors(
        background, surface, elevatedSurface, border, textPrimary, textSecondary, textMuted,
        accent, accentSurface, accentContainer, onAccent,
        success, successSurface, successText,
        warning, warningSurface, warningText,
        danger, dangerSurface, dangerText,
        info, infoSurface, infoText,
        chart1, chart2, chart3, chart4, chart5,
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
        accentSurface = other.accentSurface
        accentContainer = other.accentContainer
        onAccent = other.onAccent
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
        chart1 = other.chart1
        chart2 = other.chart2
        chart3 = other.chart3
        chart4 = other.chart4
        chart5 = other.chart5
        navBackground = other.navBackground
        navBorder = other.navBorder
        glassSurface = other.glassSurface
        glassBorder = other.glassBorder
    }
}

@Stable
class AmazeRadius(
    val xs: Dp = 8.dp,
    val small: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)

@Stable
class AmazeSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val pageHorizontal: Dp = 18.dp,
    val cardPadding: Dp = 18.dp,
    val sectionGap: Dp = 20.dp
)

@Stable
class AmazeFontSize(
    val micro: TextUnit = 10.sp,
    val xs: TextUnit = 11.sp,
    val sm: TextUnit = 12.sp,
    val base: TextUnit = 13.sp,
    val md: TextUnit = 14.sp,
    val lg: TextUnit = 16.sp,
    val xl: TextUnit = 20.sp,
    val x2l: TextUnit = 24.sp,
    val x3l: TextUnit = 32.sp,
    val display: TextUnit = 36.sp,
    val hero: TextUnit = 48.sp
)

val LocalAmazeColors = staticCompositionLocalOf<AmazeColors> {
    error("No AmazeColors provided")
}

val LocalHeroColorEnabled = staticCompositionLocalOf { true }

val LocalAmazeRadius = staticCompositionLocalOf { AmazeRadius() }
val LocalAmazeSpacing = staticCompositionLocalOf { AmazeSpacing() }
val LocalAmazeFontSize = staticCompositionLocalOf { AmazeFontSize() }
val LocalAmazeTypography = staticCompositionLocalOf { 
    AmazeTypography(
        androidx.compose.ui.text.TextStyle(),
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
    customAccent: Color = AccentOcean,
    customPalette: CustomPalette? = null,
    hapticEnabled: Boolean = true,
    animationsEnabled: Boolean = true,
    heroColorEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val accent = when (accentTheme) {
        AccentTheme.OCEAN -> AccentOcean
        AccentTheme.FOREST -> AccentForest
        AccentTheme.LAVENDER -> AccentLavender
        AccentTheme.SUNSET -> AccentSunset
        AccentTheme.CUSTOM -> customAccent
    }

    val resolvedTheme = when (appTheme) {
        AppTheme.SYSTEM -> if (isSystemInDarkTheme()) AppTheme.DARK else AppTheme.LIGHT
        else -> appTheme
    }

    val baseColors = when (resolvedTheme) {
        AppTheme.LIGHT -> AmazeColors(
            background = NeutralBgLight,
            surface = NeutralSurfaceLight,
            elevatedSurface = NeutralElevatedLight,
            border = NeutralBorderLight,
            textPrimary = NeutralTextPrimaryLight,
            textSecondary = NeutralTextSecondaryLight,
            textMuted = NeutralTextMutedLight,
            accent = accent,
            accentSurface = accent.copy(alpha = 0.12f),
            accentContainer = accent.copy(alpha = 0.25f),
            onAccent = Color(0xFF111827),
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
            chart1 = Chart1Light,
            chart2 = Chart2Light,
            chart3 = Chart3Light,
            chart4 = Chart4Light,
            chart5 = Chart5Light,
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
            accentSurface = accent.copy(alpha = 0.15f),
            accentContainer = accent.copy(alpha = 0.30f),
            onAccent = Color.White,
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
            chart1 = Chart1Dark,
            chart2 = Chart2Dark,
            chart3 = Chart3Dark,
            chart4 = Chart4Dark,
            chart5 = Chart5Dark,
            navBackground = NavBgDark,
            navBorder = NavBorderDark,
            glassSurface = GlassSurfaceDark,
            glassBorder = GlassBorderDark
        )
        AppTheme.AMOLED -> AmazeColors(
            background = Color.Black,
            surface = Color(0xFF080808),
            elevatedSurface = Color(0xFF111111),
            border = Color(0xFF222222),
            textPrimary = NeutralTextPrimaryDark,
            textSecondary = NeutralTextSecondaryDark,
            textMuted = NeutralTextMutedDark,
            accent = accent,
            accentSurface = accent.copy(alpha = 0.15f),
            accentContainer = accent.copy(alpha = 0.30f),
            onAccent = Color.White,
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
            chart1 = Chart1Dark,
            chart2 = Chart2Dark,
            chart3 = Chart3Dark,
            chart4 = Chart4Dark,
            chart5 = Chart5Dark,
            navBackground = Color.Black,
            navBorder = Color(0xFF1A1A1A),
            glassSurface = Color(0xFF0D0D0D),
            glassBorder = Color(0xFF1F1F1F)
        )
        AppTheme.SYSTEM -> error("System theme must be resolved before selecting colors")
    }

    val colors = customPalette
        ?.takeIf { it.enabled }
        ?.let { palette -> if (resolvedTheme == AppTheme.LIGHT) palette.light else palette.dark }
        ?.takeIf { !it.isEmpty }
        ?.applyTo(baseColors)
        ?: baseColors

    val rememberColors = remember { colors }.apply { updateWith(colors) }
    val radius = remember { AmazeRadius() }
    val spacing = remember { AmazeSpacing() }
    val fontSize = remember { AmazeFontSize() }
    val typography = getAmazeTypography()

    CompositionLocalProvider(
        LocalAmazeColors provides rememberColors,
        LocalAmazeRadius provides radius,
        LocalAmazeSpacing provides spacing,
        LocalAmazeFontSize provides fontSize,
        LocalAmazeTypography provides typography,
        LocalHeroColorEnabled provides heroColorEnabled,
    ) {
        com.amazecc.app.shared.ui.components.ProvideInteractionPrefs(
            hapticEnabled = hapticEnabled,
            animationsEnabled = animationsEnabled,
            content = content
        )
    }
}

object AmazeTheme {
    val colors: AmazeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeColors.current

    val heroColorEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalHeroColorEnabled.current

    val radius: AmazeRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeRadius.current

    val spacing: AmazeSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeSpacing.current

    val fontSize: AmazeFontSize
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeFontSize.current

    val typography: AmazeTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAmazeTypography.current
}
