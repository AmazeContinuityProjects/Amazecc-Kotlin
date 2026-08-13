package com.amazecc.app.shared.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

enum class PaletteMode(val label: String) {
    LIGHT("Light"), DARK("Dark")
}

/**
 * Roles of the [AmazeColors] palette that can be overridden by the palette editor.
 * The key is the storage key used in [PaletteOverrides] maps (stored as hex strings).
 */
enum class PaletteRole(val label: String) {
    BACKGROUND("Background"),
    SURFACE("Surface"),
    ELEVATED_SURFACE("Elevated Surface"),
    BORDER("Border"),
    TEXT_PRIMARY("Primary Text"),
    TEXT_SECONDARY("Secondary Text"),
    TEXT_MUTED("Muted Text"),
    ACCENT("Accent"),
    SUCCESS("Success"),
    WARNING("Warning"),
    DANGER("Danger"),
    INFO("Info"),
    CHART1("Chart 1"),
    CHART2("Chart 2"),
    CHART3("Chart 3"),
    CHART4("Chart 4"),
    CHART5("Chart 5"),
    NAV_BACKGROUND("Nav Background"),
    NAV_BORDER("Nav Border");

    companion object {
        val EDITABLE = entries
    }
}

/**
 * Hex-string overrides for one mode. `null`/absent = inherit the base palette.
 * Kept as a map so new roles never require schema migrations.
 */
@Serializable
data class PaletteOverrides(
    val values: Map<String, String> = emptyMap()
) {
    fun color(role: PaletteRole): Color? = values[role.name]?.let { parseHexColor(it) }
    fun set(role: PaletteRole, hex: String) = PaletteOverrides(values + (role.name to hex))
    fun clear(role: PaletteRole) = PaletteOverrides(values - role.name)
    val isEmpty: Boolean get() = values.isEmpty()
}

/**
 * Full custom palette: one override set per mode (dark also applies to AMOLED).
 */
@Serializable
data class CustomPalette(
    val enabled: Boolean = false,
    val light: PaletteOverrides = PaletteOverrides(),
    val dark: PaletteOverrides = PaletteOverrides()
) {
    fun overridesFor(mode: PaletteMode) = if (mode == PaletteMode.LIGHT) light else dark

    fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

    fun withRole(mode: PaletteMode, role: PaletteRole, hex: String) =
        if (mode == PaletteMode.LIGHT) copy(light = light.set(role, hex)) else copy(dark = dark.set(role, hex))

    fun clearRole(mode: PaletteMode, role: PaletteRole) =
        if (mode == PaletteMode.LIGHT) copy(light = light.clear(role)) else copy(dark = dark.clear(role))

    fun resetAll() = CustomPalette(enabled = enabled)
}

/**
 * Applies this mode's overrides on top of a base [AmazeColors] instance.
 */
fun PaletteOverrides.applyTo(colors: AmazeColors): AmazeColors = colors.copy(
    background = color(PaletteRole.BACKGROUND) ?: colors.background,
    surface = color(PaletteRole.SURFACE) ?: colors.surface,
    elevatedSurface = color(PaletteRole.ELEVATED_SURFACE) ?: colors.elevatedSurface,
    border = color(PaletteRole.BORDER) ?: colors.border,
    textPrimary = color(PaletteRole.TEXT_PRIMARY) ?: colors.textPrimary,
    textSecondary = color(PaletteRole.TEXT_SECONDARY) ?: colors.textSecondary,
    textMuted = color(PaletteRole.TEXT_MUTED) ?: colors.textMuted,
    accent = color(PaletteRole.ACCENT) ?: colors.accent,
    success = color(PaletteRole.SUCCESS) ?: colors.success,
    warning = color(PaletteRole.WARNING) ?: colors.warning,
    danger = color(PaletteRole.DANGER) ?: colors.danger,
    info = color(PaletteRole.INFO) ?: colors.info,
    chart1 = color(PaletteRole.CHART1) ?: colors.chart1,
    chart2 = color(PaletteRole.CHART2) ?: colors.chart2,
    chart3 = color(PaletteRole.CHART3) ?: colors.chart3,
    chart4 = color(PaletteRole.CHART4) ?: colors.chart4,
    chart5 = color(PaletteRole.CHART5) ?: colors.chart5,
    navBackground = color(PaletteRole.NAV_BACKGROUND) ?: colors.navBackground,
    navBorder = color(PaletteRole.NAV_BORDER) ?: colors.navBorder
)

/**
 * Returns the effective (already override-applied) color for this role from the running theme.
 * Used by the palette editor to display current values.
 */
fun PaletteRole.currentOf(colors: AmazeColors): Color = when (this) {
    PaletteRole.BACKGROUND -> colors.background
    PaletteRole.SURFACE -> colors.surface
    PaletteRole.ELEVATED_SURFACE -> colors.elevatedSurface
    PaletteRole.BORDER -> colors.border
    PaletteRole.TEXT_PRIMARY -> colors.textPrimary
    PaletteRole.TEXT_SECONDARY -> colors.textSecondary
    PaletteRole.TEXT_MUTED -> colors.textMuted
    PaletteRole.ACCENT -> colors.accent
    PaletteRole.SUCCESS -> colors.success
    PaletteRole.WARNING -> colors.warning
    PaletteRole.DANGER -> colors.danger
    PaletteRole.INFO -> colors.info
    PaletteRole.CHART1 -> colors.chart1
    PaletteRole.CHART2 -> colors.chart2
    PaletteRole.CHART3 -> colors.chart3
    PaletteRole.CHART4 -> colors.chart4
    PaletteRole.CHART5 -> colors.chart5
    PaletteRole.NAV_BACKGROUND -> colors.navBackground
    PaletteRole.NAV_BORDER -> colors.navBorder
}
