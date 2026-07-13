package com.amazecc.app.shared.services

import android.util.Log

actual class NotificationService actual constructor() {
    actual fun showLoadingNotification(title: String, message: String) {
        // In a real app, you would use NotificationManagerCompat and a Context here.
        Log.d("NotificationService", "Push Notification: $title - $message")
    }
}