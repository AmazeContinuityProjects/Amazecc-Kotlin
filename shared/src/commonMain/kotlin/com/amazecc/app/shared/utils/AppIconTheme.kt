package com.amazecc.app.shared.utils

/**
 * Selectable launcher icons (web parity — the "Application Icon" card in ProfilePage).
 *
 * [aliasSuffix] names the activity-alias declared in AndroidManifest.xml; the
 * Android actual enables exactly one of them with PackageManager and reports
 * which one is currently enabled. Desktop/iOS have no launcher icon to swap,
 * so their actuals report unsupported rather than pretending.
 */
enum class AppIconTheme(val label: String, val aliasSuffix: String) {
    DEFAULT("Default", "MainActivityDefault"),
    FIRE("Fire", "MainActivityFire");

    companion object {
        fun fromName(name: String?): AppIconTheme =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** Applies the icon theme; returns false when the platform cannot swap icons. */
expect fun setAppIconTheme(theme: AppIconTheme): Boolean

/** The currently enabled launcher icon (DEFAULT when unknown/unsupported). */
expect fun currentAppIconTheme(): AppIconTheme
