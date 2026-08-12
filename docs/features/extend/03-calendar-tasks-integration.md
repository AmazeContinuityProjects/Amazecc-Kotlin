# Calendar + Tasks Integration

## Current State
- `CalendarScreen.kt`: Shows academic calendar (holidays, exams, instructional days), Moodle assignments, exam schedule
- `DailyPlanner.kt`: Shows daily timeline with classes, free periods, lunch, exams (ExamDayGoodLuck), today's tasks
- `DashboardWidgets.kt`: ExamAndClassWidget shows next exam + next class

## Integration Requirements

### Exam/Quiz Tasks
- Appear on CalendarScreen as "Exam" type events (consolidated with existing exam consolidation logic)
- Appear on Dashboard widget (ExamDayGoodLuck) if today
- Appear on DailyPlanner timetable with exam badge

### Assignment Tasks
- Show on CalendarScreen with deadline marker (distinct from exam)
- Show "Work sessions" as free-time blocks on DailyPlanner (optional toggle)
- Show in DailyPlanner "Today's Tasks" section

### Regular Tasks
- Show on CalendarScreen (optional filter "Tasks")
- Show in DailyPlanner "Today's Tasks" section
- Show in 7-day workload view (TasksScreen + CalendarScreen)

### Deadline Linking
- All tasks with dueDate appear on CalendarScreen
- Tap event → navigate to Task detail/edit
- Long press → quick actions (complete, reschedule, delete)

---

## CalendarScreen.kt Changes
1. Add `filterTasks` toggle to filter chips
2. In `activeMonthEvents` computation: add tasks from `AppState.tasks` where `dueDate` falls in month
3. Create `ConsolidatedEvent` for tasks with type="Task", distinct color
4. Consolidation logic: group tasks by day (like exams)
5. Tap handling: navigate to TasksScreen with filter for that day

## DailyPlanner.kt Changes
1. In `buildDailySchedule`: add tasks for selected date as timeline items
2. Task items: show title, course, estimated time, type badge
3. Exam/Quiz tasks: show with exam styling (red accent)
4. Assignment work sessions: show as "Study Block" in free periods

## DashboardWidgets.kt Changes
1. ExamAndClassWidget: check `AppState.tasks` for quiz/exam tasks today
2. Show alongside or instead of exam schedule if no exams

---

## Data Flow
```
AppState.tasks (StateFlow<List<HomeworkTask>>)
    → CalendarScreen: filter by dueDate month → ConsolidatedEvent
    → DailyPlanner: filter by selectedDate → TimelineEvent
    → DashboardWidget: filter by today + type in [quiz, exam] → ExamDayGoodLuck
```

---

## Files to Modify
1. `CalendarScreen.kt` - Add task events, filter toggle
2. `DailyPlanner.kt` - Add tasks to timeline
3. `DashboardWidgets.kt` - Check tasks for exam widget
4. `AppState.kt` - Add `todayTasks` computed property (already exists)