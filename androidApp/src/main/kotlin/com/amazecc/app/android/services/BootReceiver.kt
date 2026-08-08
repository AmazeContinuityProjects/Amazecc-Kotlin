package com.amazecc.app.android.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amazecc.app.shared.utils.rescheduleAlarmsFromCache

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED && action != Intent.ACTION_TIME_CHANGED) return
        rescheduleAlarmsFromCache()
        WidgetUpdateReceiver.pushAll(context)
    }
}