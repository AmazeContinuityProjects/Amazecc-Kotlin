# Implementation Sequence & Dependencies

## Dependency Graph
```
Phase 1 (Course Details) ──┬──→ Phase 2 (Tasks Redesign) ──┬──→ Phase 3 (Calendar Integration)
                           │                              │
                           └──→ Phase 4 (Export/Import) ◄──┘
                                                    │
                           Phase 5 (Android Sync) ◄──┘
```

## Phase 1: Course Hub → Course Details Redesign (HIGHEST PRIORITY)
**Files**: `CourseDetailScreen.kt`, `CourseDashboard.kt`, `FacultyInfoScreen.kt`, `AppState.kt`
**Duration**: 3-4 sessions
**Dependencies**: None (foundational)

### Tasks:
1. Remove faculty school badge from CourseDetailsInfoCard
2. Fix marks syncing for embedded courses (merge by base code)
3. Extend CourseSubPage enum: add FACULTY, FREE_SLOTS
4. Create FacultyTab, FreeSlotsTab, redesigned CoursePlanTab
5. Update AnimatedContent navigation
6. Verify FacultyInfoScreen uses same FacultyFreeSlotsUtil (already does)

## Phase 2: Tasks Page Redesign
**Files**: `TaskModels.kt`, `AppState.kt`, `TasksScreen.kt`, `CourseDetailScreen.kt`, `AlarmReceiver.kt`
**Duration**: 4-5 sessions
**Dependencies**: Phase 1 (CourseTasksTab integration)

### Tasks:
1. Extend TaskModels.kt with new fields (type, reminderAt, reminderRepeat, showOnCalendar, showOnTimetable, workSessions, odHours)
2. Update AppState.kt task CRUD + cache load/save
3. Redesign TasksScreen.kt: Calendar view, Assignment flow (date picker + session builder), Course filter, Reminder integration
4. Update CourseDetailScreen.kt CourseTasksTab with course filter
5. Update AlarmReceiver.kt for reminder notifications

## Phase 3: Calendar + Tasks Integration
**Files**: `CalendarScreen.kt`, `DailyPlanner.kt`, `DashboardWidgets.kt`, `AppState.kt`
**Duration**: 2-3 sessions
**Dependencies**: Phase 2 (task types, showOnCalendar flags)

### Tasks:
1. CalendarScreen.kt: Add task events, filter toggle
2. DailyPlanner.kt: Add tasks to timeline, exam/quiz styling
3. DashboardWidgets.kt: Check tasks for exam widget
4. AppState.kt: todayTasks computed property (exists)

## Phase 4: Export/Import System
**Files**: `ExportImportManager.kt` (new), `SettingsManager.kt`, `AppState.kt`, Settings UI
**Duration**: 2-3 sessions
**Dependencies**: Phase 2 (tasks model), Phase 3 (all cache keys)

### Tasks:
1. Create ExportImportManager.kt with CustomExport/FullExport models
2. SettingsManager.kt: exportAllSettings/importSettings
3. AppState.kt: export/import triggers
4. Settings UI: Export/Import section

## Phase 5: Android Calendar/Tasks Sync
**Files**: `CalendarSyncManager.kt` (androidMain), `TasksSyncManager.kt` (androidMain), `AppState.kt`, Settings
**Duration**: 3-4 sessions
**Dependencies**: Phase 2 (task model), Phase 3 (calendar events), Phase 4 (settings)

### Tasks:
1. CalendarSyncManager.kt: CalendarContract sync logic
2. TasksSyncManager.kt: Tasks provider sync (API 30+)
3. AppState.kt: sync state + triggers
4. Settings: Sync toggles + calendar picker
5. Permissions handling

---

## Execution Order
1. **Phase 1** - Course Details Redesign (start now)
2. **Phase 2** - Tasks Redesign
3. **Phase 3** - Calendar Integration
4. **Phase 4** - Export/Import
5. **Phase 5** - Android Sync

---

## Verification Checklist per Phase
- [ ] Phase 1: Build compiles, CourseDetailScreen works, no school badge, marks show for embedded, new tabs navigate
- [ ] Phase 2: TasksScreen has Calendar view, Assignment flow works, reminders fire, CourseTasksTab filters
- [ ] Phase 3: Calendar shows tasks, DailyPlanner shows tasks, Dashboard widget shows quiz/exam tasks
- [ ] Phase 4: Custom export imports, Full export imports, settings preserved
- [ ] Phase 5: Calendar sync works (with permission), Tasks sync works (API 30+), conflicts handled