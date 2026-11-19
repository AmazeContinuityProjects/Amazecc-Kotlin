package com.amazecc.app.shared.utils

/**
 * Platform alarm hook for scheduled syncs.
 * Android: exact one-shot AlarmManager alarm → SyncAlarmReceiver.
 * iOS: reminder-style local notification at the due time (sync itself runs
 * when the app next comes to the foreground via AppState.checkDueSync()).
 */
expect fun scheduleSyncAlarm(triggerAtMillis: Long, kind: String)

expect fun cancelSyncAlarms()

/**
 * Whether a foreground Activity/UI is available to drive an interactive VTOP WebView login.
 *
 * A fresh VTOP login needs a WebView attached to a live window: `VtopEngine.android` throws when
 * no Activity is attached, and the fallback UI captcha prompt would otherwise park forever with no
 * UI to answer it. Background entry points (an alarm `BroadcastReceiver`, a desktop/iOS process)
 * therefore report false so the sync gate can degrade to cached-session verification instead of
 * attempting a login that physically cannot succeed.
 */
expect fun canRunInteractiveVtop(): Boolean