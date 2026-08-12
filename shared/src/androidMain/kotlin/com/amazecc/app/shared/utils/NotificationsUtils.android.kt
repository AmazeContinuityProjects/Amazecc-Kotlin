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
    val context = AndroidApp.context ?: return true
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val granted = context.checkCallingOrSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
    return granted
}

actual suspend fun scheduleLocalNotification(id: Int, title: String, body: String, triggerTimeMs: Long) {
    val context = AndroidApp.context ?: return
    val channelId = when {
        id in NotificationsUtils.ASSIGNMENT_REMINDER_ID_BASE until NotificationsUtils.EXAM_REMINDER_ID_BASE -> AlarmReceiver.CHANNEL_ASSIGNMENTS
        id in NotificationsUtils.EXAM_REMINDER_ID_BASE until NotificationsUtils.TASK_REMINDER_ID_BASE -> AlarmReceiver.CHANNEL_EXAMS
        id in NotificationsUtils.TASK_REMINDER_ID_BASE until NotificationsUtils.TEST_NOTIFICATION_ID -> AlarmReceiver.CHANNEL_TASKS
        else -> AlarmReceiver.CHANNEL_CLASSES
    }
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(AlarmReceiver.EXTRA_TITLE, title)
        putExtra(AlarmReceiver.EXTRA_BODY, body)
        putExtra(AlarmReceiver.EXTRA_CHANNEL_ID, channelId)
        putExtra(AlarmReceiver.EXTRA_NOTIFICATION_ID, id)
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
    addScheduledId(id)
}

/**
 * Cancels only the alarms that were actually scheduled (tracked per-package).
 * The flat 6000-id sweep used before created ~18k binder transactions on the
 * main thread and caused input-dispatch ANRs whenever reminders rescheduled.
 */
actual suspend fun clearPendingNotifications() {
    val context = AndroidApp.context ?: return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val ids = takeScheduledIds()
    for (id in ids) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

private fun scheduledIdsPrefs(context: Context): android.content.SharedPreferences =
    context.getSharedPreferences("amazecc_scheduled_reminder_ids", Context.MODE_PRIVATE)

private fun addScheduledId(id: Int) {
    val context = AndroidApp.context ?: return
    val current = scheduledIdsPrefs(context).getStringSet("ids", null) ?: emptySet()
    if (id.toString() in current) return
    scheduledIdsPrefs(context).edit().putStringSet("ids", current + id.toString()).apply()
}

private fun takeScheduledIds(): Set<Int> {
    val context = AndroidApp.context ?: return emptySet()
    val prefs = scheduledIdsPrefs(context)
    val ids = prefs.getStringSet("ids", null)?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()
    prefs.edit().remove("ids").apply()
    return ids
}

actual suspend fun testLocalNotification() {
    createNotificationChannels()
    val triggerTimeMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 5_000L
    scheduleLocalNotification(
        id = NotificationsUtils.TEST_NOTIFICATION_ID,
        title = "Test Notification",
        body = "If you see this, notifications are working!",
        triggerTimeMs = triggerTimeMs
    )
}

actual suspend fun createNotificationChannels() {
    val context = AndroidApp.context ?: return
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
                AlarmReceiver.CHANNEL_EXAMS, "Exam Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders before exams (24h prior and at reporting time)" },
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
