# Exam Day: Holiday Timetable + Good Luck Page + Merged Home Widget

**Status:** Planned → Built
**Files touched:**
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/ExamDayGoodLuck.kt` (new)
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/academics/DailyPlanner.kt`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/DashboardWidgets.kt`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt`

---

## 1. Problem

1. On an exam day (per the VTOP exam schedule), the Daily Planner still shows the
   normal class timeline, with only a small `ExamDayBanner` pinned above it.
   Students want the timetable to "turn into a holiday" on exam days.
2. The home screen has two separate widgets — `CURRENT_NEXT_CLASS` and
   `EXAM_ALERT` — that are redundant and fight for the same space.

## 2. Decisions (confirmed with product owner)

| # | Question | Decision |
|---|----------|----------|
| 1 | What happens to the old `EXAM_ALERT` widget in saved configs? | **Auto-removed.** The two widgets merge into one new widget. |
| 2 | When is the merged widget in "exam mode"? | **From the time the last class ends until the exam ends** (per the exam day's timetable). Falls back to the exam start time when there are no classes that day, or when classes end after the exam. |
| 3 | Which pages get the exam-day holiday treatment? | Calendar + Attendance predictor already treat exam days correctly. **Only the timetable (Daily Planner) needed the fix.** |

## 3. Design

### 3.1 Daily Planner (timetable) — `DailyPlannerScreen`

When the selected date has exams (`examsForDate.isNotEmpty()`):

- The page is replaced by a **full-page "Good Luck" state**:
  - Big celebratory hero ("🎉", "Good Luck!", date)
  - Exam cards with course code, title, time, session, venue, seat (reuses
    `ExamItem` display helpers: `sessionDisplay`, `seatLocationDisplay`)
  - Live countdown chip per exam: *Starts in 2h 15m* → *In progress* → *Done*
- A **"Preview Timetable"** toggle button reveals the underlying day schedule
  (classes during exam windows are already suppressed by `buildDailySchedule`).
- Day chips in the week selector show **"Exam"** instead of the class count.

### 3.2 Merged home widget — `EXAM_AND_CLASS`

New single widget that replaces `CURRENT_NEXT_CLASS` + `EXAM_ALERT`:

- **Exam mode** (active window, see table above): renders today's exams in a
  compact list with countdown + urgency colors (borrowed from `ExamAlertWidget`),
  tap → Exam Schedule screen.
- **Normal mode** (otherwise): the existing Current & Next Class tracker UI.
- Default position: where `CURRENT_NEXT_CLASS` used to sit (slot 2 of the enum).

### 3.3 Widget migration — `AppState.loadWidgetOrder()`

- `CURRENT_NEXT_CLASS` → migrated to `EXAM_AND_CLASS` **in place** (keeps its
  relative position for existing users).
- `EXAM_ALERT` → **dropped** from the saved order (merged).
- Unknown/old names are skipped; duplicates de-duplicated. Empty result falls
  back to the full default list.

## 4. Implementation notes

- New composable `ExamDayGoodLuck(exams, showTimetable, onToggleTimetable)` lives
  in `ui/components/` so it can be reused anywhere later (e.g. calendar day view).
- `DashboardWidget` enum order changes: `PROFILE_HEADER, METRIC_CARDS,
  EXAM_AND_CLASS, ATTENDANCE_BUNK, TODAYS_CLASSES, COURSE_ATTENDANCE,
  QUICK_ACTIONS, FREE_CLASSROOMS` (8 widgets, was 9).
- All persisted widget lists are regenerated via migration; no settings version
  bump needed (migration is idempotent).
