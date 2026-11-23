package com.amazecc.app.android.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
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

            // D-191: land the tap on the feature screen, not the app root. The deep-link URI is
            // the contract; target_screen keeps working on hosts where the shared deep-link queue
            // has no consumer yet (MainActivity handles that extra directly).
            val intent = Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("amazecc://tab?tab=free-class")
                putExtra("target_screen", "FREE_CLASSROOMS")
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // D-191 follow-up: the shared layer now resolves "now" through the canonical week
            // index. A blank type marks its honest empty state (no live period / no synced data),
            // never a guessed free room.
            fun showEmpty(message: String) {
                views.setViewVisibility(R.id.widget_room1_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_room1_title, message)
                views.setTextViewText(R.id.widget_room1_type, "")
                views.setViewVisibility(R.id.widget_room2_container, View.GONE)
                views.setViewVisibility(R.id.widget_room3_container, View.GONE)
            }

            try {
                val rooms = WidgetDataUtils.getFreeClassroomsNow()
                val emptyState = rooms.firstOrNull { it.type.isBlank() }
                if (emptyState != null) {
                    showEmpty(emptyState.room)
                } else {
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
                }
            } catch (_: Exception) {
                showEmpty("No classroom data synced yet")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
