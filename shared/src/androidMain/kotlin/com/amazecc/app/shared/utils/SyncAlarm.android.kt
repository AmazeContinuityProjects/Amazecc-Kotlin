package com.amazecc.app.shared.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.amazecc.app.shared.services.AndroidApp
import com.amazecc.app.shared.services.SyncAlarmReceiver

const val SYNC_ALARM_REQUEST_CODE = 9173

actual fun scheduleSyncAlarm(triggerAtMillis: Long, kind: String) {
    val context = AndroidApp.context ?: return
    val intent = Intent(context, SyncAlarmReceiver::class.java)
        .putExtra(SyncAlarmReceiver.EXTRA_KIND, kind)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        SYNC_ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}

actual fun cancelSyncAlarms() {
    val context = AndroidApp.context ?: return
    val intent = Intent(context, SyncAlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        SYNC_ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(pendingIntent)
}