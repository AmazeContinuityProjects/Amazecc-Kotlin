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

class UpcomingTasksWidgetProvider : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.upcoming_tasks_widget_layout)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            try {
                val tasks = WidgetDataUtils.getUpcomingTasks()
                views.setTextViewText(R.id.widget_task_count, "${tasks.size} Tasks")

                if (tasks.isEmpty()) {
                    views.setViewVisibility(R.id.widget_empty_tasks, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_task1_container, View.GONE)
                    views.setViewVisibility(R.id.widget_task2_container, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_empty_tasks, View.GONE)

                    val task1 = tasks.getOrNull(0)
                    if (task1 != null) {
                        views.setViewVisibility(R.id.widget_task1_container, View.VISIBLE)
                        views.setTextViewText(R.id.widget_task1_code, task1.courseCode.ifBlank { "GENERAL" })
                        views.setTextViewText(R.id.widget_task1_title, task1.title)
                        views.setTextViewText(R.id.widget_task1_date, task1.dueDate.ifBlank { "Due Soon" })
                        views.setTextViewText(R.id.widget_task1_subtasks, if (task1.subtasksProgress.isNotBlank()) "Subtasks: ${task1.subtasksProgress}" else "Priority: ${task1.priority}")
                    } else {
                        views.setViewVisibility(R.id.widget_task1_container, View.GONE)
                    }

                    val task2 = tasks.getOrNull(1)
                    if (task2 != null) {
                        views.setViewVisibility(R.id.widget_task2_container, View.VISIBLE)
                        views.setTextViewText(R.id.widget_task2_code, task2.courseCode.ifBlank { "GENERAL" })
                        views.setTextViewText(R.id.widget_task2_title, task2.title)
                        views.setTextViewText(R.id.widget_task2_date, task2.dueDate.ifBlank { "Upcoming" })
                        views.setTextViewText(R.id.widget_task2_subtasks, if (task2.subtasksProgress.isNotBlank()) "Subtasks: ${task2.subtasksProgress}" else "Priority: ${task2.priority}")
                    } else {
                        views.setViewVisibility(R.id.widget_task2_container, View.GONE)
                    }
                }
            } catch (_: Exception) {
                views.setViewVisibility(R.id.widget_empty_tasks, View.VISIBLE)
                views.setViewVisibility(R.id.widget_task1_container, View.GONE)
                views.setViewVisibility(R.id.widget_task2_container, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
