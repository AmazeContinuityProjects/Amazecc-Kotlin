# Tasks Page Redesign (Course-Specific + Global)

## Current State
- `TasksScreen.kt`: List, Kanban, Workload views; filters (all/pending/today/done/lms); AddTaskBottomSheet
- `TaskModels.kt`: `HomeworkTask` with basic fields (id, courseCode, title, dueDate, type, priority, estimatedMinutes, subtasks, etc.)
- `CourseDetailScreen.kt` → `CourseTasksTab`: filters tasks by courseCode(s)

## New Requirements

### Task Types Extension
Extend `HomeworkTask.type`: `homework`, `quiz`, `exam`, `lab`, `project`, `assignment`, `lms_auto`

### Assignment Type Flow
When type=`assignment`:
- Ask for **submission deadline** (dueDate)
- Ask user to **pick work dates** and **duration per session** (multiple sessions before deadline)
- Optional: **repeating reminders** (daily/weekly/custom) until deadline
- Auto-split into daily work sessions if user prefers

### Quiz/Exam Tasks → Calendar/Timetable
- When creating quiz/exam task: toggle "Show on Calendar" and "Show on Timetable"
- If enabled: appear on CalendarScreen, Dashboard widget (ExamDayGoodLuck), DailyPlanner timetable
- Regular classes: only appear if user toggles "Include regular classes" in task creation

### Views
- **List** (existing)
- **Kanban** (To Do / In Progress / Done) - existing
- **Workload** (7-day density) - existing
- **Calendar** (new) - monthly view with task dots, tap day → tasks list

### Filters
- All, Pending, Today, Done, LMS Auto, **By Course**

### Course-Specific Tasks
- `CourseDetailScreen` → `CourseTasksTab`: filter by courseCode(s) (theory + lab codes for embedded)
- Global Tasks screen shows all tasks

### Reminders
- Add `reminderAt` field (ISO datetime)
- Add `reminderRepeat` field: `none`, `daily`, `weekly`, `custom`
- Integrate with `AlarmReceiver` + notification channels

### LMS Auto-Sync
- Moodle assignments auto-create tasks with `isAutoSynced=true`, `type="lms_auto"`

### OD Hours Tracking
- Add `odHours: Double` field to task

### Redesign Parity
- Global Tasks + Add/Edit sheet use same visual language as CourseDetail Overview:
  - Hero card (workload summary)
  - SettingsGroupCard menu (view modes, filters)
  - AmazeCard sections

---

## TaskModels.kt Additions
```kotlin
@Serializable
data class HomeworkTask(
    ...
    val type: String = "homework",           // homework, quiz, exam, assignment, project, lab, lms_auto
    val reminderAt: String? = null,          // ISO datetime for notification
    val reminderRepeat: String = "none",     // none, daily, weekly, custom
    val showOnCalendar: Boolean = false,     // quiz/exam only
    val showOnTimetable: Boolean = false,
    val workSessions: List<WorkSession> = emptyList(), // for assignment type
    val odHours: Double = 0.0,
    ...
)

@Serializable
data class WorkSession(
    val date: String,        // YYYY-MM-DD
    val startTime: String,   // HH:mm
    val durationMinutes: Int
)

@Serializable
data class AssignmentPlan(
    val deadline: String,           // YYYY-MM-DD
    val sessions: List<WorkSession>
)
```

---

## Files to Modify
1. `TaskModels.kt` - Add new fields
2. `AppState.kt` - Task CRUD for new fields; load/save tasks cache
3. `TasksScreen.kt` - Redesign: Calendar view, Assignment flow, Course filter, Reminder integration
4. `CourseDetailScreen.kt` - CourseTasksTab integration with course filter
5. `CalendarScreen.kt` - Show tasks as events; filter toggles
5. `DailyPlanner.kt` - Show tasks in timeline; exam/quiz badges
6. `DashboardWidgets.kt` - ExamAndClassWidget shows quiz/exam tasks
7. `AlarmReceiver.kt` - Handle reminder notifications

---

## Implementation Sequence
1. `TaskModels.kt` - Add new fields
2. `AppState.kt` - Task CRUD + cache load/save
3. `TasksScreen.kt` - Calendar view, Assignment flow, Course filter, Reminders
4. `CourseDetailScreen.kt` - CourseTasksTab course filter
5. `CalendarScreen.kt` - Task events integration
6. `DailyPlanner.kt` - Timeline integration
7. `DashboardWidgets.kt` - Quiz/exam task display
8. `AlarmReceiver.kt` - Reminder notifications