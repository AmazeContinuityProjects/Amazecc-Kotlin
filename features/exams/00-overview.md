# Exam Awareness Feature — Overview

**Status**: Planning
**Target**: AmazeCC-Kotlin (KMP + Compose, `shared` module)
**Date**: 2026-08-10

---

## What this feature does

Make exams first-class citizens across the app. On exam days the app stops being a
class-first app and becomes an exam-first app:

1. **Calendar** — exam days show full exam details in the event list: exam code, title,
   exam type (FAT/CAT…), date, time, session (FN/AN), reporting time, venue, seat number
   and seat location (`R8C7`).
2. **Notifications** — exam reminders fire per exam time (at reporting time − lead offset,
   and at T−24h). Class reminders are **cancelled for the whole exam day** and replaced by
   the exam reminders. A new `Exam Reminders` toggle in Settings controls this.
3. **Normal timetable** — both the Daily Planner (timeline) and the Timetable Grid show an
   exam banner on exam days with full details, and day chips are flagged `EXAM`.
4. **Exam Schedule screen** — cards upgraded with slot/session/reporting-time/seat-location,
   plus TODAY / IN-x-days highlighting and date-sorted groups.
5. **Home screen** — a 24-hour exam alert widget renders only when an exam is within 24h or
   today, showing exam code, title, venue, reporting time, session, seat no and seat
   location, with a live countdown. Tap → Exam Schedule screen.

## User stories

- As a student, I want to open the calendar on an exam day and see my seat (`R8C7`),
  venue, reporting time and session without hunting through pages.
- As a student, I want a reminder at reporting time so I reach the hall on time, and a
  24h-before heads-up so I know what's coming.
- As a student, I don't want class reminders on exam days — exams replace classes that day.
- As a student, I want the home screen to surface "your exam is in 6h at AB3-402, seat
  R8C7" automatically, and only when it matters.

## Decisions locked in (with user, 2026-08-10)

| Question | Decision |
|---|---|
| Class suppression scope | **Whole exam day** — any date with ≥1 exam gets no class reminders; exam reminder fires instead |
| Exam notification timing | **Reporting time − lead offset** (reuses "Class Reminder Lead Time") **+ T−24h** notification (only if it lands in the future) |
| Home 24h card | **Auto-widget** — renders only when active; toggleable/orderable in Manage Widgets |
| Data scope | **Semester selected in the Exam Schedule dropdown** (`selectedExamSemester`); falls back to scanning all synced semesters only if the selected one has no exams |

## Deliverables

- `features/exams/01-research.md` — codebase + API findings, gaps, bugs found
- `features/exams/02-design.md` — architecture, data flow, notification & widget design
- `features/exams/03-implementation-plan.md` — file-by-file build steps
- `features/exams/04-testing.md` — unit tests + manual QA checklist
