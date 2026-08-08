package com.amazecc.app.android.services

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REFRESH) {
            pushAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.amazecc.app.android.action.REFRESH_WIDGETS"

        fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            fun updateAll(component: ComponentName, update: (Context, AppWidgetManager, Int) -> Unit) {
                runCatching {
                    for (id in manager.getAppWidgetIds(component)) update(context, manager, id)
                }
            }
            updateAll(ComponentName(context, TodayClassesWidgetProvider::class.java), TodayClassesWidgetProvider::updateAppWidget)
            updateAll(ComponentName(context, AttendanceStatsWidgetProvider::class.java), AttendanceStatsWidgetProvider::updateAppWidget)
            updateAll(ComponentName(context, UpcomingTasksWidgetProvider::class.java), UpcomingTasksWidgetProvider::updateAppWidget)
            updateAll(ComponentName(context, FreeClassroomsWidgetProvider::class.java), FreeClassroomsWidgetProvider::updateAppWidget)
            updateAll(ComponentName(context, QuickActionsWidgetProvider::class.java), QuickActionsWidgetProvider::updateAppWidget)
        }
    }
}