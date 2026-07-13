package com.amazecc.app.shared.services

expect class NotificationService() {
    fun showLoadingNotification(title: String, message: String)
}