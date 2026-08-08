package com.amazecc.app.shared.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.UserNotifications.UNUserNotificationCenter

private const val SYNC_REMINDER_NOTIFICATION_ID = 9010
private const val SYNC_REMINDER_IDENTIFIER = "amazecc_9010"

actual fun scheduleSyncAlarm(triggerAtMillis: Long, kind: String) {
    CoroutineScope(Dispatchers.Main).launch {
        NotificationsUtils.scheduleLocalNotification(
            id = SYNC_REMINDER_NOTIFICATION_ID,
            title = "AmazeCC Sync",
            body = if (kind == "full") {
                "Weekly full refresh is due. Open the app to sync."
            } else {
                "Daily refresh is due. Open the app to sync."
            },
            triggerTimeMs = triggerAtMillis
        )
    }
}

actual fun cancelSyncAlarms() {
    UNUserNotificationCenter.currentNotificationCenter()
        .removePendingNotificationRequestsWithIdentifiers(listOf(SYNC_REMINDER_IDENTIFIER))
}