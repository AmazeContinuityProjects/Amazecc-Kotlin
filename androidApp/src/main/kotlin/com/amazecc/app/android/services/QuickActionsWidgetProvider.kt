package com.amazecc.app.android.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.amazecc.app.android.MainActivity
import com.amazecc.app.android.R

class QuickActionsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.quick_actions_widget_layout)

            fun createPendingIntent(route: String, reqCode: Int): PendingIntent {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("target_screen", route)
                }
                return PendingIntent.getActivity(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

            views.setOnClickPendingIntent(R.id.btn_action_attendance, createPendingIntent("ATTENDANCE", 101))
            views.setOnClickPendingIntent(R.id.btn_action_tasks, createPendingIntent("TASKS", 102))
            views.setOnClickPendingIntent(R.id.btn_action_ffcs, createPendingIntent("FFCS_PLANNER", 103))
            views.setOnClickPendingIntent(R.id.btn_action_freerooms, createPendingIntent("FREE_CLASSROOMS", 104))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
