package com.amazecc.app.android.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.amazecc.app.android.MainActivity
import com.amazecc.app.android.R
import com.amazecc.app.shared.utils.WidgetDataUtils
import java.util.Calendar

class TodayClassesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.today_classes_widget_layout)

            // Click intent to open main app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            try {
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val currentMins = hour * 60 + minute

                val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val dayOfWeekStr = dayNames.getOrElse(calendar.get(Calendar.DAY_OF_WEEK) - 1) { "Today" }
                val monthStr = monthNames.getOrElse(calendar.get(Calendar.MONTH)) { "" }
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                views.setTextViewText(R.id.widget_date_text, "$dayOfWeekStr, $monthStr $dayOfMonth")

                val data = WidgetDataUtils.getScheduleData()
                val ongoing = data.ongoing
                val next = data.next

                fun formatMinutes(m: Int): String = when {
                    m <= 0 -> "Now"
                    m >= 60 -> "${m / 60}h ${m % 60}m"
                    else -> "${m}m"
                }

                if (data.totalToday == 0) {
                    views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                    views.setTextViewText(R.id.widget_empty_text, "☕ No classes scheduled today")
                    views.setViewVisibility(R.id.widget_current_container, View.GONE)
                    views.setViewVisibility(R.id.widget_next_container, View.GONE)
                } else if (data.isDone) {
                    views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                    views.setTextViewText(R.id.widget_empty_text, "🎉 All classes done for today!")
                    views.setViewVisibility(R.id.widget_current_container, View.GONE)
                    views.setViewVisibility(R.id.widget_next_container, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_empty_text, View.GONE)

                    if (ongoing != null) {
                        views.setViewVisibility(R.id.widget_current_container, View.VISIBLE)
                        val leftMins = (ongoing.endMins - currentMins).coerceAtLeast(0)
                        views.setTextViewText(R.id.widget_current_time, "Ends in ${formatMinutes(leftMins)}")
                        views.setTextViewText(R.id.widget_current_title, ongoing.title)
                        views.setTextViewText(R.id.widget_current_code, ongoing.code)
                        views.setTextViewText(R.id.widget_current_venue, " 📍 ${ongoing.venue}")
                    } else {
                        views.setViewVisibility(R.id.widget_current_container, View.GONE)
                    }

                    if (next != null) {
                        views.setViewVisibility(R.id.widget_next_container, View.VISIBLE)
                        val startIn = (next.startMins - currentMins).coerceAtLeast(0)
                        views.setTextViewText(R.id.widget_next_time, "In ${formatMinutes(startIn)}")
                        views.setTextViewText(R.id.widget_next_title, next.title)
                        views.setTextViewText(R.id.widget_next_code, next.code)
                        views.setTextViewText(R.id.widget_next_venue, " 📍 ${next.venue}")
                    } else {
                        views.setViewVisibility(R.id.widget_next_container, View.GONE)
                    }
                }
            } catch (_: Exception) {
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty_text, "AmazeCC • Open app to sync")
                views.setViewVisibility(R.id.widget_current_container, View.GONE)
                views.setViewVisibility(R.id.widget_next_container, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
