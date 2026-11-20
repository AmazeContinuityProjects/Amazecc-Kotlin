package com.amazecc.app.shared.utils

import android.content.ComponentName
import android.content.pm.PackageManager
import com.amazecc.app.shared.services.AndroidApp

private fun aliasComponent(theme: AppIconTheme): ComponentName? {
    val context = AndroidApp.context ?: return null
    return ComponentName(context.packageName, "${context.packageName}.${theme.aliasSuffix}")
}

actual fun setAppIconTheme(theme: AppIconTheme): Boolean {
    val context = AndroidApp.context ?: return false
    val packageManager = context.packageManager
    AppIconTheme.entries.forEach { candidate ->
        val component = aliasComponent(candidate) ?: return false
        val state = if (candidate == theme) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
    return true
}

actual fun currentAppIconTheme(): AppIconTheme {
    val context = AndroidApp.context ?: return AppIconTheme.DEFAULT
    val packageManager = context.packageManager
    return AppIconTheme.entries.firstOrNull { theme ->
        val component = aliasComponent(theme) ?: return@firstOrNull false
        val state = packageManager.getComponentEnabledSetting(component)
        // A manifest-enabled alias reports DEFAULT (not ENABLED), so accept that
        // state only for the default theme.
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (theme == AppIconTheme.DEFAULT && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    } ?: AppIconTheme.DEFAULT
}
