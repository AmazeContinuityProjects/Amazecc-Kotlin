package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean = false
)

@Serializable
data class WorkSession(
    val date: String,           // YYYY-MM-DD
    val startTime: String,      // HH:mm
    val durationMinutes: Int
)

@Serializable
data class HomeworkTask(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val title: String,
    val description: String = "",
    val dueDate: String,
    val dueTime: String = "23:59",
    val type: String = "homework", // homework, quiz, exam, assignment, project, lab, lms_auto
    val priority: String = "medium", // high, medium, low
    val estimatedMinutes: Int = 0,
    val actualMinutesSpent: Int = 0,
    val completed: Boolean = false,
    val subtasks: List<Subtask> = emptyList(),
    val isAutoSynced: Boolean = false,
    val createdAt: String,
    val reminderAt: String? = null,          // "YYYY-MM-DD HH:mm" notification
    val reminderRepeat: String = "none",     // none, daily, weekly, custom
    val showOnCalendar: Boolean = false,     // quiz/exam visibility
    val showOnTimetable: Boolean = false,    // quiz/exam visibility
    val includeRegularClasses: Boolean = false,
    val workSessions: List<WorkSession> = emptyList(), // assignment type
    val odHours: Double = 0.0
)
