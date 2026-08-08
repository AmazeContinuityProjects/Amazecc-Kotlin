package com.amazecc.app.shared.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.SyncScheduler

class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: SyncScheduler.LIGHT_KIND
        AppState.runScheduledSync()
    }

    companion object {
        const val EXTRA_KIND = "sync_kind"
    }
}