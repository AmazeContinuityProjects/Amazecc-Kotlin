# Exam Awareness — Implementation Plan

Order matters: model → utils → notifications → calendar → screens → widget → wiring.
Every step is independently compilable (`./gradlew :shared:compileDebugKotlin` or
`androidApp:compileDebugKotlin`).

## Step 1 — Model: extend `ExamItem`

**File**: `shared/src/commonMain/kotlin/com/amazecc/app/shared/model/ScheduleModels.kt`

- [ ] Add `classId`, `examSession`, `reportingTime`, `seatLocation` (all `= ""`) to `ExamItem`
- [ ] Verify old cached JSON still decodes (defaults cover missing keys) — no cache-busting needed

## Step 2 — `ExamUtils` (pure logic)

**New file**: `shared/src/commonMain/kotlin/com/amazecc/app/shared/utils/ExamUtils.kt`

- [ ] `parseExamDateToLocalDate(raw: String): LocalDate?` — numeric + `DD-Mon-YYYY` + `DD/Mon/YYYY`
- [ ] `examTimeToMinutes(raw: String): Int?` — first time of `"09:00 AM"` / `"09:15 AM - 12:30 PM"`
- [ ] `examsForDate(exams, date)`, `sortedExamDays(exams)`, `isExamDate(exams, date)`
- [ ] `nextExamWithin(exams, now, withinHours)` + `hoursUntilExam`
- [ ] `calculateSeatLocation(seatNo, courseTitle)` — port of web algorithm incl. course exemptions
- [ ] `ExamItem.seatLocationDisplay` / `ExamItem.sessionDisplay` extension properties
- [ ] Unit tests (see Step 8 — write in same PR)

## Step 3 — Notifications

**Files**: `NotificationsUtils.kt`, `NotificationsUtils.android.kt`,
`AlarmReceiver.kt`, `SettingsManager.kt`, `SettingsDataPages.kt`, `SettingsHub.kt`

- [ ] `NotificationsUtils.EXAM_REMINDER_ID_BASE = 3000`; add 3000–3999 to `scheduleableNotificationIds`
- [ ] `scheduleExamReminders(exams: List<ExamItem>)` — T−24h + reporting-time−offset
- [ ] `scheduleClassReminders(..., examDays: Set<LocalDate>)` — skip whole exam days
- [ ] `scheduleAll(..., exams: List<ExamItem>? = null)` + `rescheduleFromCache()` (decode `CACHE_ALL_SEMESTER_EXAMS`, selected semester first)
- [ ] `AppState.scheduleReminders()` (line 1395) — pass exams from `_examSchedule` (selected sem; fallback scan all)
- [ ] `AlarmReceiver.CHANNEL_EXAMS = "amazecc_exams"`; `createNotificationChannels()` adds it (IMPORTANCE_HIGH)
- [ ] Android `scheduleLocalNotification` id→channel: `id in 3000 until 4000 -> CHANNEL_EXAMS`
- [ ] iOS actual: add exam id range branch (no-op like today)
- [ ] `SettingsManager.NOTIF_EXAM_REMINDERS` + `isNotifExamRemindersEnabled()` / setter
- [ ] Settings UI: "Exam Reminders" toggle row (icon `EventSeat`, subtitle mentions 24h + class suppression) → `AppState.rescheduleNotifications()` on change
- [ ] `SettingsHub` summary count includes exam reminders

## Step 4 — Calendar

**File**: `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/CalendarScreen.kt`

- [ ] Replace `parseExamDateParts` with `ExamUtils.parseExamDateToLocalDate`
- [ ] `ConsolidatedEvent` + `exam: ExamItem?`; exam merge block passes full `ExamItem`
- [ ] New `ExamEventCard(exam, examType)` (own file `ExamCards.kt` or inside CalendarScreen.kt) with date/time/session/reporting/venue/seat(loc+no) grid + TODAY/PAST badge
- [ ] Events list renders `ExamEventCard` when `ev.exam != null`, else `BouncyEventCard`

## Step 5 — Exam Schedule screen

**File**: `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/ExamScheduleScreen.kt`

- [ ] Slot badge; Session + Reporting time + Seat location (`Loc:`/`No:`) rows
- [ ] Status badge TODAY / PAST / IN {n}d|h; dim past cards
- [ ] Sort groups by `ExamUtils.sortedExamDays`

## Step 6 — Daily Planner & Timetable Grid

**Files**: `TimetableComponents.kt` (new shared banner),
`DailyPlanner.kt`, `AttendanceScreen.kt` (`TimetableGridScreen`)

- [ ] `ExamDayBanner(exams)` composable in `TimetableComponents.kt` (compact rows, +N more)
- [ ] DailyPlanner: `examMap` per LocalDate; day chips show `EXAM`; banner above timeline
- [ ] TimetableGridScreen: chips marker + banner in day overview/selected day

## Step 7 — Home 24h widget

**Files**: `AppState.kt` (enum), `DashboardWidgets.kt`

- [ ] `DashboardWidget.EXAM_ALERT`; `getWidgetTitle`/`getWidgetDescription`
- [ ] `ExamAlertWidget()` — auto-hide when no exam within 24h; countdown ticker 60s; tap → `Screen.EXAM_SCHEDULE`
- [ ] Default widget order includes it (new installs); no migration for existing users
- [ ] Verify Manage Widgets / HiddenWidgets / reorder paths compile with the new enum entry

## Step 8 — Tests

**File**: `shared/src/commonTest/kotlin/.../ExamUtilsTest.kt`

- [ ] Date parsing: `19-Nov-2025`, `2025-11-19`, `19/11/2025`, garbage → null
- [ ] `examTimeToMinutes`: `09:00 AM`→540, `09:15 AM - 12:30 PM`→555, PM wrap
- [ ] `calculateSeatLocation`: seat 41 → R5C3 (matches web demo data), exemption courses → `-`
- [ ] `nextExamWithin` boundary (exactly 24h in / 1 minute past)
- [ ] `examsForDate` / `isExamDate`
- [ ] Class-suppression set: exam day excluded from reminder loop (function-level via extracted pure helper if needed)

## Step 9 — Verification

- [ ] `./gradlew :shared:compileDebugKotlin` and `./gradlew androidApp:compileDebugKotlin`
- [ ] `./gradlew :shared:testDebugUnitTest` (or existing test task)
- [ ] Manual QA per `04-testing.md`
