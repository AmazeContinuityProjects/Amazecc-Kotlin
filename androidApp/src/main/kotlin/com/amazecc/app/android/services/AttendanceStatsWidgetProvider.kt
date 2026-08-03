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
import com.amazecc.app.shared.utils.WidgetDataUtils

class AttendanceStatsWidgetProvider : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.attendance_stats_widget_layout)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            try {
                val stats = WidgetDataUtils.getAttendanceStats()
                views.setTextViewText(R.id.widget_pct_value, stats.overallPercentage)
                views.setTextViewText(R.id.widget_cgpa_value, stats.cgpa)
                views.setTextViewText(R.id.widget_credits_value, stats.earnedCredits)
                views.setTextViewText(R.id.widget_od_value, stats.odHours)
                views.setTextViewText(R.id.widget_status_badge, if (stats.isSafe) "SAFE" else "WARN")
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_pct_value, "--%")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
