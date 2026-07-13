package com.amazecc.app.shared.services

import platform.Foundation.NSLog

actual class NotificationService actual constructor() {
    actual fun showLoadingNotification(title: String, message: String) {
        // In a real app, you would use UNUserNotificationCenter here.
        NSLog("Push Notification: %s - %s", title, message)
    }
}