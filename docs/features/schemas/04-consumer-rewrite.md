# Unified Academic Schema — Consumer Rewrite

Every consumer of the 8 removed academic flows, with the new read pattern.
Compose consumers read:

```kotlin
val academic by AppState.academic.collectAsState()
val selectedSem by AppState.selectedSemester.collectAsState()
// then, once per frame-safe derive:
val sem = academic.semesters[selectedSem]
val courses = sem?.courses  // Map<String, StoredCourse>
val exams = sem?.exams      // List<ExamItem>
```

Process contexts (widget/notification) use `AppDataStore.loadPersistedSnapshot().academic`.

New API surface (see `05-store-and-sync.md`):
- `AppState.academic: StateFlow<AcademicData>` (delegates to store)
- `AppState.selectedSemester` (unchanged)
- `AppState.selectSemester(id)` (unchanged)
- removed: `attendance`, `marks`, `allGrades`, `allSemesterAttendance`,
  `allSemesterMarks`, `allSemesterExams`, `examSchedule`, `timetable` flows.

---

## Tier 1 — screens (collectAsState)

| File | Lines (old) | Old reads | New read pattern |
|---|---|---|---|
| `ui/screens/academics/AttendanceScreen.kt` | 167, 169, 1237, 1238 | attendance, examSchedule, attendance+timetable (class schedule tab) | `academic.semesters[selected]`; exams → `sem.exams`; weekly timetable → `AcademicDerivers.buildWeeklyTimetable(sem)` |
| `ui/screens/academics/AcademicsScreen.kt` | 51–54 | marks, attendance, timetable, allGrades (metric cards + GPA records) | `sem.courses` + `sem.gpa`; GPA records iterate `academic.semesters` |
| `ui/screens/academics/GradesScreen.kt` | 56, 57 | allGrades, marks | `academic.semesters`; per-sem `sem.gpa`; selected-sem grades from `sem.courses[code].grade` |
| `ui/screens/academics/GPAPredictorScreen.kt` | 53, 54 | marks, attendance | `sem.courses` — credits from `course.credits`, current marks from `course.marks.assessments`, attendance % from `course.attendance.percentage` |
| `ui/screens/academics/ExamScheduleScreen.kt` | 51, 52 | allSemesterExams, examSchedule | `academic.semesters` → `sem.exams` (all sems); selected via `selectedExamSemester`-equivalent → `sem.exams` |
| `ui/screens/academics/CurriculumScreen.kt` | 143–145, 152 | curriculum (KEEP), allGrades, marks, attendance (completed-codes set) | curriculum unchanged; completed codes from `academic.semesters.values.flatMap { it.courses.values }` where `grade?.grade` is non-blank and NOT in `F`/`N` (matches old non-fail behavior; doc note: 04 draft said blank-only) |
| `ui/screens/academics/CourseDashboard.kt` | 43–48, 467–509 | allSemesterMarks, allSemesterAttendance, attendance, marks, allGrades, timetable; `buildSemesterGroups()` | DELETE `buildSemesterGroups` → iterate `academic.semesters`; per sem: `sem.courses`; per course: `course.grade`, `course.attendance`, `course.marks`, `course.slots` |
| `ui/screens/academics/CourseDetailScreen.kt` | 189–194, 710, 1985–2050, 2037 | allSemesterMarks, allSemesterAttendance, attendance, marks, allGrades, timetable; `findCourseGroup()` | DELETE `findCourseGroup` → `sem.courses[courseCode]` for the course's own sem; other-sems occurrence: scan `academic.semesters` for the code. `GradeHistoryTab` reads `sem.courses[code].grade` per sem |
| `ui/screens/academics/CourseAttendanceScreen.kt` | 54, 95 | attendance | `sem.courses[code].attendance` (logs, counts, percentage) |
| `ui/screens/academics/ODTrackerScreen.kt` | 131 | attendance | `sem.courses.values` → `.attendance.logs` (existing OD logic unchanged) |
| `ui/screens/academics/DailyPlanner.kt` | 75 | attendance | `buildWeeklyTimetable(sem)` or `sem.courses` for day mapping |
| `ui/screens/academics/CalendarScreen.kt` | 232, 233 | examSchedule, selectedSemester | `sem.exams` for selected sem |
| `ui/screens/academics/TasksScreen.kt` | 82, 2354 | attendance (subject filter + stats) | `sem.courses.values` (title/code list + attendance %) |
| `ui/screens/SocialScreen.kt` | 126, 520 | attendance (percent chips) | `sem.courses` → `course.attendance.percentage` |

## Tier 2 — shared components

| File | Lines (old) | Old reads | New read pattern |
|---|---|---|---|
| `ui/components/DashboardWidgets.kt` | 870–871, 1079, 1204, 1774, 2068–2069 | marks, attendance, allSemesterAttendance (today's classes, OD counter, semester switch) | `academic.semesters[selected]`; today's classes via `buildWeeklyTimetable`; OD hours via `computeODHours`; all-sem stats iterate `academic.semesters` |
| `ui/components/ExamCards.kt` | 291, 293 | allSemesterExams, examSchedule | `academic.semesters[sem]?.exams` |
| `ui/components/CommandRegistry.kt` | 32, 33, 298 | attendance, marks, curriculum (percent command, open course) | `academic.semesters[selected]`; curriculum read unchanged |

## Tier 3 — process contexts

| File | Lines (old) | Old reads | New read pattern |
|---|---|---|---|
| `utils/WidgetDataUtils.kt` | 70, 127, 128, 172 | `AppDataStore.attendance.value`, `marks.value` | `loadPersistedSnapshot().academic.semesters[<widget's semester>]` → courses; `computeODHours(sem)` moved to `AcademicDerivers` |
| `utils/NotificationsUtils.kt` | 328 (selectedSemesterExams), 341–351 (rescheduleFromCache) | `allSemesterExams`, snapshot fields `lms`/`tasks`/`ffcsRegistration`/`attendance` | `snapshot.academic.semesters.values.flatMap { it.exams }` filtered by exam-semester ID; others unchanged |
| `utils/ExportImportManager.kt` | whole | `AppDataStore.exportSnapshot()`, legacy `cache_*` skip | unchanged calls — v2 shape flows through; v1 `appData` decode via `SnapshotMigrator` (see `03-migration.md`) |
| `ui/screens/FeedbackStatusScreen.kt` | 286, 291 | allGrades, selectedSemester (cached-grades hint) | `academic.semesters[selected]?.gpa` / course count |

## Tier 4 — AppState internals (rewired, see 05)

- Write sites: 881, 910, 913, 1039, 1042, 1055–1060, 1131, 1145, 1309–1322,
  1355, 1361–1363, 1454–1509, 1527–1539, 1810–1811, 1849, 1859, 2449, 2452, 2487.
- Internal reads: 524 (grades for CGPA/percent), 881/1810 (mirrors — DELETED),
  1310/1497 (gap-fill), 1355 (attendance), 1361–1363 (exam alerts/reminders).

## Tier 5 — unchanged (verify only)

- `MoreScreen`, `SettingsHub`, `SettingsDataPages`, `SettingsPages`,
  `OnboardingScreen` — `selectedSemester` only; `selectSemester` keeps
  behavior (no mirror writes to delete — those lived in `refresh*`).
- `AttendanceTimetable.kt` (util) — builds `CourseAttendanceInfo` grids from
  attendance + weekly timetable; verify it uses the derived view.
- `App.kt` — GPA predictor navigation only.

## Order of work

1. Models (`AppModels.kt` v2) + `AcademicMerge`/`AcademicDerivers` +
   `SnapshotMigrator` + `LegacySnapshot`.
2. `AppDataStore` API swap (upserts, `academic` flow, remove setters/flows).
3. `AppState` sync rewiring (delete mirrors, gap-fill over `semesters`).
4. Tier 1 screens (biggest surface — CourseDashboard/CourseDetailScreen last,
   they are the join-heavy ones).
5. Tier 2–4 (widgets, notifications, feedback screen).
6. Migration/backup verification + tests + compile.
