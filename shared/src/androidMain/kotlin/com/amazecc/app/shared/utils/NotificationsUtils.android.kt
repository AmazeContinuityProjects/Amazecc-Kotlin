package com.amazecc.app.shared.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.amazecc.app.shared.services.AlarmReceiver
import com.amazecc.app.shared.services.AndroidApp

actual suspend fun requestNotificationPermissions(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val context = AndroidApp.context
    return context.checkCallingOrSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

actual suspend fun scheduleLocalNotification(id: Int, title: String, body: String, triggerTimeMs: Long) {
    val context = AndroidApp.context
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(AlarmReceiver.EXTRA_TITLE, title)
        putExtra(AlarmReceiver.EXTRA_BODY, body)
        putExtra(AlarmReceiver.EXTRA_CHANNEL_ID, AlarmReceiver.CHANNEL_CLASSES)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context, id, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }
    } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
    }
}

actual suspend fun clearPendingNotifications() {
    val context = AndroidApp.context
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

actual suspend fun testLocalNotification() {
    createNotificationChannels()
    val triggerTimeMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 5_000L
    scheduleLocalNotification(
        id = 9999,
        title = "Test Notification",
        body = "If you see this, notifications are working!",
        triggerTimeMs = triggerTimeMs
    )
}

actual suspend fun createNotificationChannels() {
    val context = AndroidApp.context
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                AlarmReceiver.CHANNEL_CLASSES, "Class Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for upcoming classes" },
            NotificationChannel(
                AlarmReceiver.CHANNEL_ASSIGNMENTS, "Assignment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for upcoming assignment deadlines" },
            NotificationChannel(
                AlarmReceiver.CHANNEL_VITOL, "VITOL Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Warnings about VITOL trip limits" },
            NotificationChannel(
                AlarmReceiver.CHANNEL_SYNC, "Sync Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background sync status updates" },
            NotificationChannel(
                AlarmReceiver.CHANNEL_TASKS, "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for homework and tasks" }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }
}
