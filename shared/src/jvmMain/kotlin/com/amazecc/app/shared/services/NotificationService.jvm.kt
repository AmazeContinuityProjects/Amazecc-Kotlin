package com.amazecc.app.shared.services

/**
 * Desktop prints the loading notification instead of posting one. Keeps the call sites
 * branch-free and gives a visible trace while exercising a sync by hand.
 */
actual class NotificationService {
    actual fun showLoadingNotification(title: String, message: String) {
        println("[notification] $title — $message")
    }
}
