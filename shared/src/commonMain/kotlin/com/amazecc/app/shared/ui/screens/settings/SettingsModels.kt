package com.amazecc.app.shared.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsGroup(val label: String) {
    PERSONALIZE("Personalize"),
    DATA("Data & Sync"),
    ACCOUNT("Account"),
    DANGER("Danger Zone")
}

enum class SettingsSubScreen(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val group: SettingsGroup
) {
    APPEARANCE("Appearance & Feel", "Theme, accent colors & interactions", Icons.Rounded.Palette, SettingsGroup.PERSONALIZE),
    PALETTE("Custom Palette", "Override every color role per mode", Icons.Rounded.Adjust, SettingsGroup.PERSONALIZE),
    DISPLAY("Display & Layout", "Attendance format & UI zoom", Icons.Rounded.Visibility, SettingsGroup.PERSONALIZE),
    DASHBOARD("Dashboard Layout", "Home style, session density & widget order", Icons.Rounded.DashboardCustomize, SettingsGroup.PERSONALIZE),
    BOTTOM_NAV("Bottom Navigation", "Pinned tabs, order & live preview", Icons.Rounded.Navigation, SettingsGroup.PERSONALIZE),
    ACADEMICS("Academics & Semester", "Target semester & default calendar", Icons.Rounded.School, SettingsGroup.DATA),
    DATA_SYNC("Data, Sync & Alerts", "Notifications, sync engine & storage", Icons.Rounded.Sync, SettingsGroup.DATA),
    VTOP_SOURCE("VTOP Connection", "Where VTOP data is fetched from, plus diagnostics", Icons.Rounded.CloudSync, SettingsGroup.DATA),
    CREDENTIALS("Credentials", "Saved VTOP, Moodle & Library logins", Icons.Rounded.Lock, SettingsGroup.ACCOUNT),
    ABOUT("About AmazeCC", "Version, changelog & credits", Icons.Rounded.Info, SettingsGroup.ACCOUNT),
    DANGER("Danger Zone", "Clear local cache & log out", Icons.Rounded.Warning, SettingsGroup.DANGER)
}
