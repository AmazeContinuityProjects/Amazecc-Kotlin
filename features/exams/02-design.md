# Exam Awareness — Design

## 1. Architecture overview

```
AmazeClient.getExamSchedule(semId)
        │  POST /schedule
        ▼
ExamScheduleRes { schedule: Map<type, List<ExamItem>> }   (ExamItem extended: +classId, +examSession, +reportingTime, +seatLocation)
        │
        ▼
AppState.allSemesterExams / examSchedule / selectedExamSemester
        │
        ├─► ExamUtils (pure Kotlin, commonMain, unit-testable)
        │      parseExamDate · examTimeToMinutes · examsForDate · nextExamWithin24h
        │      calculateSeatLocation · seatLocationDisplay · isExamDate
        │
        ├─► CalendarScreen        — ConsolidatedEvent.exam + ExamEventCard
        ├─► ExamScheduleScreen    — upgraded cards (session/reporting/seat-loc/TODAY)
        ├─► DailyPlannerScreen    — ExamDayBanner + EXAM day chips
        ├─► TimetableGridScreen   — ExamDayBanner + EXAM day chips
        ├─► DashboardWidgets      — ExamAlertWidget (24h auto-widget)
        └─► NotificationsUtils    — scheduleExamReminders + class suppression
               └─ Android: AlarmReceiver.CHANNEL_EXAMS (new), ID range 3000–3999
```

## 2. Data model changes

### `ExamItem` (ScheduleModels.kt) — additive only

```kotlin
@Serializable
data class ExamItem(
    val courseCode: String = "",
    val courseTitle: String = "",
    val classId: String = "",      // NEW
    val slot: String = "",
    val examDate: String = "",
    val examSession: String = "",  // NEW  e.g. "FN1", "AN1"
    val reportingTime: String = "",// NEW  e.g. "09:00 AM"
    val examTime: String = "",
    val venue: String = "",
    val seatLocation: String = "", // NEW  e.g. "R5C3" or "-"
    val seatNo: String = ""
)
```

Backward compatible: existing cached JSON (`CACHE_EXAM_SCHEDULE`,
`CACHE_ALL_SEMESTER_EXAMS`) decodes with defaults for missing fields; new fields survive
the next sync. Cached old data simply shows "TBD" for the new rows until refresh.

## 3. `ExamUtils` (new `shared/.../utils/ExamUtils.kt`)

All pure functions, no AppState dependency, `commonMain`:

| Function | Purpose |
|---|---|
| `parseExamDateToLocalDate(raw: String): LocalDate?` | Handles `19-Nov-2025`, `2025-11-19`, `19/11/2025`, `19-11-2025` (numeric); month names via `jan..dec` mapping. This is the single source of truth — replaces `CalendarScreen.parseExamDateParts` |
| `examTimeToMinutes(raw: String): Int?` | Parses `"09:00 AM"` / `"09:15 AM - 12:30 PM"` → minutes since midnight of the first time (reporting/start) |
| `examsForDate(exams: Iterable<ExamItem>, date: LocalDate): List<ExamItem>` | Filters by date; null/invalid dates skipped |
| `sortedExamDays(exams): List<ExamItem>` | Date + time + course code sort (mirrors web `compareExamDates`) |
| `isExamDate(exams, date): Boolean` | `examsForDate().isNotEmpty()` — drives class-notif suppression |
| `nextExamWithin(exams, now, withinHours: Long): ExamItem?` | First exam with `start(now, examDate, reportingTime) - now in (0, withinHours]`; uses reporting time as start |
| `hoursUntilExam(exam, now): Double?` | Fractional hours until exam start |
| `calculateSeatLocation(seatNo: String, courseTitle: String): String` | Port of web algorithm (ScheduleDisplay.tsx:426): group = (n-1)/18; C1 = group*2+1, C2 = C1+1; pos=(n-1)%18; row = pos/2+1; col = pos%2==0?C1:C2; `-` for exempt course prefixes (Qualitative/Quantitative/French/German/Spanish/Japanese) or invalid n |
| `ExamItem.seatLocationDisplay: String` (extension) | `seatLocation` when not blank/`-`, else `calculateSeatLocation(seatNo, courseTitle)`, else `"TBD"` |
| `ExamItem.sessionDisplay: String` | `FN`/`AN` → `"Forenoon"`/`"Afternoon"` when parseable, else raw |

## 4. Notification design

### ID space & channel

- `EXAM_REMINDER_ID_BASE = 3000` (range 3000–3999, currently free between assignments and tasks).
- New channel `AlarmReceiver.CHANNEL_EXAMS = "amazecc_exams"`, `IMPORTANCE_HIGH`,
  description "Reminders for upcoming exams". Created in
  `NotificationsUtils.android.kt createNotificationChannels()`.
- `scheduleLocalNotification` id→channel mapping (NotificationsUtils.android.kt:33):
  add `id in 3000 until 4000 -> CHANNEL_EXAMS`.

### `scheduleExamReminders(exams: List<ExamItem>)`

Per exam (only when `NOTIF_EXAM_REMINDERS` enabled, permission granted, channel created):

1. **T−24h notification** — trigger = `reportingStart(now-24h)`; scheduled only if
   `trigger > now`. Title: `Exam Tomorrow` / body:
   `{courseCode} · {courseTitle} — {examDate}, {reportingTime} @ {venue} (Seat {seatNo}, {loc})`.
2. **Reporting-time notification** — trigger = reportingStart − `NOTIF_OFFSET_MINUTES`
   (reuses the existing "Class Reminder Lead Time" setting). Title: `Exam in {offset}m`,
   body: `{courseCode} · {courseTitle} — Report by {reportingTime}, {venue} · Session {session} · Seat {seatNo} ({loc})`.
3. If `reportingTime` blank → fall back to start of `examTime`; if both blank → skip exam.

`exams` passed in must be from the **selected exam semester** (see scope decision), with
fallback to scanning all semesters when the selected semester has no exams.

### Class reminder suppression (whole exam day)

`scheduleClassReminders` (NotificationsUtils.kt:41) currently loops `today..today+6`.
Change signature to accept `examDays: Set<LocalDate>` (computed from the same exam list)
and `continue` for any `targetDate in examDays` — no class notification for that day.
`clearPendingNotifications()` + full reschedule already happens on each
`scheduleAll`/`rescheduleFromCache` call, so cancelled class alarms are dropped naturally.

### Wiring points

| Call site | Change |
|---|---|
| `NotificationsUtils.scheduleAll(...)` | add `exams: List<ExamItem>?` param; call `scheduleExamReminders` |
| `NotificationsUtils.rescheduleFromCache()` | decode `CACHE_ALL_SEMESTER_EXAMS` (or selected-semester cache), pass exams |
| `AppState.scheduleReminders()` (line 1395) | read `_examSchedule.value` (selected semester) → exams; pass to `scheduleAll` |
| `AppState.refreshExamSchedule()` / `loadAllData()` exam module | after cache update, `scheduleReminders()` is already invoked post-sync (`finally` block line 1389) — no extra call needed |

### Settings

- `SettingsManager.NOTIF_EXAM_REMINDERS = "notif_exam_reminders"` + getter/setter
  (default `false`, same pattern as other toggles).
- `SettingsDataPages.kt` "Notifications & Alerts" group: add "Exam Reminders" row
  (icon `Icons.Rounded.EventSeat`, subtitle "Remind before exams & 24h prior; suppresses
  class reminders on exam days"). Toggle → `AppState.rescheduleNotifications()`.
- `SettingsHub.kt:41` summary count includes exam reminders.

## 5. Calendar design (CalendarScreen.kt)

1. Delete/replace `parseExamDateParts` with `ExamUtils.parseExamDateToLocalDate`; delete
   private month-name parsing duplication where possible.
2. `ConsolidatedEvent` gains `val exam: ExamItem? = null`.
3. Exam events added (line 338 block) carry the full `ExamItem`, keeping `type="Exam"`,
   color `chart1`, title `${courseCode} (${type})`.
4. New `ExamEventCard(exam, examType)` composable rendered by the events list when
   `ev.exam != null` (replaces generic `BouncyEventCard` for those rows):
   - Header: course code (accent, bold) + type badge + `TODAY`/`PAST` badge
   - Course title
   - Grid rows: **Date** · **Time** (examTime) · **Session** · **Reporting time** ·
     **Venue** · **Seat** (`R8C7 · No. 41` via `seatLocationDisplay`)
   - Tap → `AppState.navigateTo(Screen.EXAM_SCHEDULE)`
5. Keep exam consolidation/ranges as-is (month overview), but **when a day is selected**
   (`selectedDay != null`) individual exam cards show full detail — currently the range
   logic only runs for month view (getConsolidatedEventsForDisplay handles this).
6. Grid cell highlighting (`hasExam`, chart1) unchanged — now actually works because the
   date parse bug is fixed.

## 6. Exam Schedule screen (ExamScheduleScreen.kt)

Card upgrade, per exam:
- Course code + title (existing)
- **Slot badge** when `slot` present and `!= "-"`
- Type is already the section header (FAT/CAT-1…)
- Info rows: Date, Time (examTime), **Session**, **Reporting time**, Venue,
  **Seat location** (`Loc: R8C7`) + **Seat no** (`No: 41`)
- Status badge: `TODAY` (chart1/success), `PAST` (muted, dimmed card), `IN {n}d` /
  `IN {h}h` (info) — relative to now
- Sort each group by `sortedExamDays` (date, then time, then code)

## 7. Daily Planner & Timetable Grid

Shared composable `ExamDayBanner(exams: List<ExamItem>, modifier)` — placed in
`TimetableComponents.kt` (already a shared components file for timetable UI):

- Accent-bordered card, `EventSeat` icon, "EXAM DAY" label, one compact row per exam:
  `code · title — {examTime} · {venue} · Session {session} · Seat {loc} #{seatNo}`.
- Collapsible if >2 exams (show first 2 + "+N more").

**DailyPlanner.kt**:
- Collect `examSchedule` (selected exam semester, fallback scan).
- `examMap: Map<LocalDate, List<ExamItem>>` via `ExamUtils`.
- Day chips: when `examMap[wd.fullDate] != null` show `EXAM` label (chart1) instead of
  class count / Off.
- Above the timeline (after the day-order override banner): `ExamDayBanner` for the
  selected date. Classes remain visible (suppression is notification-only).

**TimetableGridScreen (AttendanceScreen.kt)**:
- Same exam map; in the overview list each day card gets an exam line; in day-selector
  chips append `⚡`-style marker (e.g. `EXAM` badge under the day name, chart1).
- Selected-day view: `ExamDayBanner` above the slot grid.
- `computeImportantDates` already covers exams — unchanged.

## 8. Home screen 24h widget

### `DashboardWidget.EXAM_ALERT` (AppState.kt enum, line 2871)

- Added to enum; `getWidgetTitle`: "Exam Alert (24h)"; `getWidgetDescription`: "Next exam
  within 24 hours with venue & seat details".
- **Auto-hide**: `WidgetContent` renders `ExamAlertWidget()`; the composable itself
  returns empty (`Unit`) when no exam qualifies — the widget row collapses visually
  (Box wraps empty content; LazyColumn still reserves the key — acceptable: content is
  zero-height). Manage dialog + hidden-widgets row work like any other widget.
- Default order: existing users keep their saved `widgetOrder` (no migration — widget
  appears in "Hidden Widgets" until enabled); new installs include it via the default
  widget list in `loadWidgetOrder()`.

### `ExamAlertWidget()` (DashboardWidgets.kt)

State: `AppState.allSemesterExams` + `AppState.selectedExamSemester`. Compute
`next = ExamUtils.nextExamWithin(examsOfSelectedSemester.ifEmpty { all semesters }, now, 24)`.

Card layout (AmazeCard):
- Header: `EventSeat` icon + "EXAM ALERT" + live countdown badge
  (`In 12h 30m` / `TODAY · In 45m`), recomputed every 60s via `LaunchedEffect` loop
  (pattern already used by `CurrentNextClassWidget`).
- Course code (accent) + title
- Rows: 📍 Venue · 🕐 Reporting time · Session · Seat `loc #{seatNo}`
- Click → `AppState.navigateTo(Screen.EXAM_SCHEDULE)`.

## 9. iOS parity note

`NotificationsUtils` is `expect/actual`. `scheduleLocalNotification` iOS actual
(iosMain) is a no-op stub today; adding the exam branch is a one-line `when` there too
(no channel concept). Calendar/home/timetable are shared Compose — no platform work.

## 10. Edge cases

| Case | Handling |
|---|---|
| `seatLocation == "-"` | compute from `seatNo` + course title; exempt soft-skill/language courses → `-` → display `TBD` |
| Blank `reportingTime` | fall back to `examTime` start; else skip notif |
| Exam today but reporting time already passed | T−24h notif not scheduled (in past); reporting notif only if trigger > now |
| Two exams same day | two notifications; suppression is per-day |
| Semester switch (dropdown) | `selectExamSemester` updates `examSchedule` → next sync/`scheduleReminders` refresh; widget + notifs follow selected semester |
| Old cached data (pre-model-change) | missing fields default to "" → "TBD" display; next sync fills them |
| DST / timezone | all times computed via `kotlinx.datetime` `Instant` in current system TZ, same as existing schedulers |
