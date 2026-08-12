# Exam Awareness — Research

## 1. Data source & API

**Endpoint**: `AmazeClient.getExamSchedule(semesterId)` → `POST /schedule` (AmazeClient.kt:238)
**Response model**: `ExamScheduleRes` (MiscModels.kt:71) — `Schedule`/`schedule` map of
`examType -> List<ExamItem>` (FAT, CAT-1, CAT-2, CAT-3, Lab, …).

**API payload fields** (from `AmazeCC` web `src/data/demoData.json` + `schedule.d.ts`):

| Field | Example | Currently in Kotlin? |
|---|---|---|
| `courseCode` | `BMAT201L` | ✅ |
| `courseTitle` | `Complex Variables and Linear Algebra` | ✅ |
| `classId` | `CH2025260100834` | ❌ dropped |
| `slot` | `A2+TA2+TAA2` | ✅ |
| `examDate` | `19-Nov-2025` (DD-Mon-YYYY) | ✅ (raw string) |
| `examSession` | `FN1` / `AN1` | ❌ dropped |
| `reportingTime` | `09:00 AM` | ❌ dropped |
| `examTime` | `09:15 AM - 12:30 PM` | ✅ (raw string) |
| `venue` | `AB3-402` | ✅ |
| `seatLocation` | `R5C3` | ❌ dropped |
| `seatNo` | `41` | ✅ |

**Key gap**: Kotlin `ExamItem` (ScheduleModels.kt:6) only models
`courseCode, courseTitle, slot, examDate, examTime, venue, seatNo`. The backend already
sends `classId, examSession, reportingTime, seatLocation` — they are silently dropped by
kotlinx.serialization (unknown keys ignored). Adding them is a pure model addition; no
API change needed.

## 2. Web app parity (reference implementation)

`AmazeCC/src/components/custom/exams/ScheduleDisplay.tsx`:

- Shows per-exam: course, exam time, **session**, **reporting time**, venue, seat
  `Loc: R5C3 / No: 41`.
- **Seat-location fallback** (ScheduleDisplay.tsx:426):
  ```
  calculateSeatLocation(seatNo, courseTitle) -> "R{row}C{col}"
  - exempts courses starting with Qualitative/Quantitative/French/German/Spanish/Japanese
  - groupIndex = floor((n-1)/18); C1 = groupIndex*2+1; C2 = C1+1
  - pos = (n-1)%18; row = floor(pos/2)+1; col = pos%2==0 ? C1 : C2
  ```
- Today's-exams summary panel (green) + per-type grouped cards, past/today/upcoming
  styling.
- ICS export with `computeExamTimes` (CAT 1h45m, FAT 3h30m durations) — not in scope for
  v1.

## 3. Current Kotlin app state

### Exam data in AppState (AppState.kt)
- `examSchedule: StateFlow<ExamScheduleRes?>` (line 526)
- `allSemesterExams: StateFlow<Map<String, ExamScheduleRes?>>` (line 528)
- `selectedExamSemester: StateFlow<String>` — default `"CH20262701"` (line 530)
- `selectExamSemester(id)` (line 1871), `refreshExamSchedule()` (line 1879)
- Caches: `CACHE_EXAM_SCHEDULE`, `CACHE_ALL_SEMESTER_EXAMS` (SettingsManager.kt:74,89)
- Sync module `SyncModule.EXAM_SCHEDULE` exists (SyncEngine.kt:34)

### Calendar (CalendarScreen.kt)
- Already merges exams into `activeMonthEvents` (line 338) as
  `ConsolidatedEvent("${courseCode} ($type)", "Exam", "${examTime} · ${venue}", chart1)`.
- **BUG 1**: `parseExamDateParts` (line 56) splits on `-`/`/` and calls `.toInt()` on every
  part. `"19-Nov-2025"` → `"Nov".toInt()` throws → caught → `Triple(0,0,0)` → exam is
  silently dropped from the calendar. Only numeric `DD-MM-YYYY` / `YYYY-MM-DD` works.
- **BUG 2**: month-name handling in `parseMonthString` (line 72) parses `"July 2026"` fine,
  but `parseExamDateParts` never hands month names to it — the two parsers are inconsistent.
- Consolidation logic (`getConsolidatedEventsForDisplay`, line 95) merges exam days into
  ranges — exam type grouping regex-based.
- Event cards (`BouncyEventCard`, line 831) show title + type badge + time/location only.

### Daily Planner (DailyPlanner.kt)
- Week day selector with holiday detection (line 96 `holidayMap`), timeline of classes
  (`TimelineEvent` type: class/free/lunch). No exam awareness.

### Timetable Grid (AttendanceScreen.kt `TimetableGridScreen`, line 804)
- Day-wise slot grid + `View Full Timetable` dialog; `computeImportantDates` (line 618)
  already folds exam dates in for the "Important Dates" chip (line 672 fallback).

### Notifications
- `NotificationsUtils` (commonMain): `scheduleClassReminders` (line 41, loops 7 days,
  builds notifyInstant from `SlotMap` times), `scheduleAssignmentReminders`, `scheduleTaskReminders`,
  `scheduleAll` (line 197), `rescheduleFromCache` (line 181, called on boot from
  `WidgetSync.android.kt:20`).
- ID ranges (line 29): `CLASS_REMINDER_ID_BASE=1000`, `ASSIGNMENT_REMINDER_ID_BASE=2000`,
  `TASK_REMINDER_ID_BASE=4000`, `TEST_NOTIFICATION_ID=9999`. **3000–3999 is free.**
- Android actual (NotificationsUtils.android.kt): `scheduleLocalNotification` maps id→
  channel (line 33); `createNotificationChannels` (line 109) creates CLASSES/ASSIGNMENTS/
  VITOL/SYNC/TASKS via `AlarmReceiver.CHANNEL_*` (AlarmReceiver.kt:63-67).
- Settings keys: `NOTIF_CLASS_REMINDERS`, `NOTIF_ASSIGNMENT_REMINDERS`, `NOTIF_TASK_REMINDERS`,
  `NOTIF_OFFSET_MINUTES` (SettingsManager.kt:108-111; getters line 238-250).
- Settings UI: `SettingsDataPages.kt:163` "Notifications & Alerts" group with three toggles;
  "Class Reminder Lead Time" preset row (line 219); `SettingsHub.kt:41` summary counts.
- Reschedule triggers: `AppState.scheduleReminders()` after every sync (AppState.kt:1395-1409,
  called from sync `finally` at line 1389), settings toggles, boot (`rescheduleFromCache`).

### Dashboard
- Widget system: `DashboardWidget` enum (AppState.kt:2871), persisted `widgetOrder`
  (line 2758), `WidgetDashboard` (DashboardWidgets.kt:80) renders `WidgetContent(widget)`
  (line 601), titles/descriptions (line 578/589), manage dialog + hidden-row logic.
- Existing widgets: PROFILE_HEADER, METRIC_CARDS, CURRENT_NEXT_CLASS, ATTENDANCE_BUNK,
  TODAYS_CLASSES, COURSE_ATTENDANCE, QUICK_ACTIONS, FREE_CLASSROOMS.

## 4. Bugs / gaps found during research

1. `parseExamDateParts` cannot parse `DD-Mon-YYYY` → exams missing from calendar cells.
2. `ExamItem` drops `classId`, `examSession`, `reportingTime`, `seatLocation` from API.
3. Calendar exam event card has no seat/venue split, no session/reporting time.
4. No exam notifications at all; class reminders fire even on exam days.
5. No exam awareness in Daily Planner / Timetable Grid.
6. No home-screen exam visibility.

## 5. Out of scope (v1)

- ICS export / calendar add (web parity has it; iOS/Android native calendar intent later)
- Exam result/marks integration
- `classId` usage (kept on model for future, not displayed)
