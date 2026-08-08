package com.amazecc.app.shared.utils

/**
 * Platform alarm hook for scheduled syncs.
 * Android: exact one-shot AlarmManager alarm → SyncAlarmReceiver.
 * iOS: reminder-style local notification at the due time (sync itself runs
 * when the app next comes to the foreground via AppState.checkDueSync()).
 */
expect fun scheduleSyncAlarm(triggerAtMillis: Long, kind: String)

expect fun cancelSyncAlarms()