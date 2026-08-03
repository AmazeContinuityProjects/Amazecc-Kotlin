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

class FreeClassroomsWidgetProvider : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.free_classrooms_widget_layout)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            try {
                val rooms = WidgetDataUtils.getFreeClassroomsSample()

                val r1 = rooms.getOrNull(0)
                if (r1 != null) {
                    views.setViewVisibility(R.id.widget_room1_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_room1_title, "📍 ${r1.room}")
                    views.setTextViewText(R.id.widget_room1_type, r1.type)
                } else {
                    views.setViewVisibility(R.id.widget_room1_container, View.GONE)
                }

                val r2 = rooms.getOrNull(1)
                if (r2 != null) {
                    views.setViewVisibility(R.id.widget_room2_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_room2_title, "📍 ${r2.room}")
                    views.setTextViewText(R.id.widget_room2_type, r2.type)
                } else {
                    views.setViewVisibility(R.id.widget_room2_container, View.GONE)
                }

                val r3 = rooms.getOrNull(2)
                if (r3 != null) {
                    views.setViewVisibility(R.id.widget_room3_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_room3_title, "📍 ${r3.room}")
                    views.setTextViewText(R.id.widget_room3_type, r3.type)
                } else {
                    views.setViewVisibility(R.id.widget_room3_container, View.GONE)
                }
            } catch (_: Exception) {
                views.setViewVisibility(R.id.widget_room1_container, View.GONE)
                views.setViewVisibility(R.id.widget_room2_container, View.GONE)
                views.setViewVisibility(R.id.widget_room3_container, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
