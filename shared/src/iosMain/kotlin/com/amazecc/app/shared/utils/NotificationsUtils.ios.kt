package com.amazecc.app.shared.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.*
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume

actual suspend fun requestNotificationPermissions(): Boolean = suspendCancellableCoroutine { cont ->
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    ) { granted, _ ->
        cont.resume(granted)
    }
}

actual suspend fun scheduleLocalNotification(id: Int, title: String, body: String, triggerTimeMs: Long) {
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound())
    }
    val triggerTimeSec = triggerTimeMs / 1000.0
    val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
        triggerTimeSec - NSDate().timeIntervalSince1970,
        repeats = false
    )
    val request = UNNotificationRequest.requestWithIdentifier(
        "amazecc_$id",
        content,
        trigger
    )
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { _ -> }
}

actual suspend fun clearPendingNotifications() {
    UNUserNotificationCenter.currentNotificationCenter().removeAllPendingNotificationRequests()
}

actual suspend fun createNotificationChannels() {
    // iOS handles channels differently — categories are used instead
    // UNUserNotificationCenter categories would be configured here if needed
}
