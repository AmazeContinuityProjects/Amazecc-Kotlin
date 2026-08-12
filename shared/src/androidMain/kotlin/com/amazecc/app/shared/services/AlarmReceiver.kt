package com.amazecc.app.shared.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.amazecc.app.shared.utils.NotificationsUtils

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "AmazeCC Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: CHANNEL_CLASSES
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val tapIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent()

        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        }

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifyId = if (notificationId in NotificationsUtils.scheduleableNotificationIds) notificationId else generateId(title)
            nm.notify(notifyId, notification)
        } catch (_: SecurityException) { }
    }

    private fun generateId(title: String): Int = title.hashCode() and Int.MAX_VALUE

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_BODY = "alarm_body"
        const val EXTRA_CHANNEL_ID = "alarm_channel_id"
        const val EXTRA_NOTIFICATION_ID = "alarm_notification_id"
        const val CHANNEL_CLASSES = "amazecc_classes"
        const val CHANNEL_ASSIGNMENTS = "amazecc_assignments"
        const val CHANNEL_EXAMS = "amazecc_exams"
        const val CHANNEL_VITOL = "amazecc_vitol"
        const val CHANNEL_TASKS = "amazecc_tasks"
        const val CHANNEL_SYNC = "amazecc_sync"
    }
}
