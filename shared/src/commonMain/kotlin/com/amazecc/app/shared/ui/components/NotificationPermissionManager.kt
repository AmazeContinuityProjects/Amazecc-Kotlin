package com.amazecc.app.shared.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

interface NotificationPermissionManager {
    fun requestPermission()
}

val LocalNotificationPermissionManager = staticCompositionLocalOf<NotificationPermissionManager?> { null }
