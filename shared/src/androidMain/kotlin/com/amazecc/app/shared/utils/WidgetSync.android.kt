package com.amazecc.app.shared.utils

import android.content.Intent
import com.amazecc.app.shared.services.AndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

actual fun pushWidgetUpdates() {
    val context = AndroidApp.context ?: return
    runCatching {
        context.sendBroadcast(Intent("com.amazecc.app.android.action.REFRESH_WIDGETS").setPackage("com.amazecc.app.android"))
    }
}

actual fun rescheduleAlarmsFromCache() {
    val context = AndroidApp.context ?: return
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        runCatching { NotificationsUtils.rescheduleFromCache() }
    }
    runCatching {
        context.sendBroadcast(Intent("com.amazecc.app.android.action.REFRESH_WIDGETS").setPackage("com.amazecc.app.android"))
    }
}