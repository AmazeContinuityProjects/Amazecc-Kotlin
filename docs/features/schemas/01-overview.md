# Unified Academic Schema — Overview & Goals

Status: PLANNED (2026-08-15) — implementation not started.

## Why

The app-data snapshot currently stores **ten overlapping structures** for academic
data. The same course is described five times with overlapping fields, and the
single-semester mirrors (`attendance`, `marks`, `examSchedule`) duplicate the
per-semester maps (`allSemesterAttendance`, `allSemesterMarks`, `allSemesterExams`)
for the selected semester.

### Redundancy analysis (verified against a real full backup, 2026-08-15)

| Domain | Stored shape | Course identity fields duplicated elsewhere |
|---|---|---|
| attendance | `AttendanceRes{ attendance: List<AttendanceItem> }` | code, title, type, slots, faculty, venue, category, credits |
| marks | `MarksRes{ courses: List<MarksCourseItem>, cgpa }` | code, title, type, system, faculty, slot, classNbr |
| grades | `AllGradesRes{ grades: Map<sem, { gpa, grades: [GradeItem] }> }` | code, title, type |
| timetable | `TimetableRes{ courseInfo, slots }` | code, title, type, slots, venue, faculty, classId, category, credits (LTPJC) |
| exam schedule | `ExamScheduleRes{ schedule: Map<date, [ExamItem]> }` | code, title, classId, slot |
| curriculum | `CurriculumRes{ categories, details }` | catalog-only (basket code/name/credits/type) |

Cross-cutting duplication:

1. **Course identity stored 5×** — one course code appears with its title/type/
   slots/venue/faculty in attendance, marks, grades, timetable, and exam items.
2. **Mirror fields** — `attendance`, `marks`, `examSchedule` hold the same objects
   as `allSemester*[selectedSem]`, kept in sync by ~8 manual mirror writes in
   `AppState.kt` (e.g. lines 881, 1810: `setExamSchedule(allSemesterExams.value[semesterId])`).
   A missed mirror write is a silent stale-data bug class.
3. **Manual joins at read time** — `CourseDashboard.buildSemesterGroups()` and
   `CourseDetailScreen.findCourseGroup()` zip marks × attendance × grades ×
   timetable per semester by course code on every recomposition.
4. **Sanitization runs per domain** — the same course code/title is cleaned
   independently in 5 sanitizers (`sanitizeAttendance`, `sanitizeMarks`,
   `sanitizeAllGrades`, `sanitizeTimetable`, `sanitizeExamSchedule`).

## Goals

- One `StoredCourse` record per (semester, courseCode) carrying every known
  field: identity, slots, venue, faculty, attendance, marks, grade.
- One semester map replaces the 8 academic snapshot fields; single-semester
  "views" are projections, not storage.
- Sync writes one domain into the semester via upsert — no mirror writes, no
  cross-domain bookkeeping in `AppState`.
- CGPA predictor, attendance %, course dashboard, curriculum completion, and
  timetable reads all become single-record lookups.
- Snapshot size shrinks (identity dedup; logs remain the bulk).

## Scope decisions (confirmed)

1. **Scope**: only the ten academic domains (grades, curriculum-sibling
   untouched otherwise, attendance, timetable, marks, allGrades,
   allSemesterAttendance, allSemesterMarks, allSemesterExams, examSchedule).
   Calendar, hostel, mess, laundry, counselling, payments, library, transport,
   buses, LMS, events, clubs, circulars, moodle, cab share, FFCS, tasks, QCM
   stay exactly as they are today.
2. **Consumer API**: FULL consumer rewrite. The 10 academic `StateFlow`s
   (`AppState.attendance`, `marks`, `allGrades`, `allSemesterAttendance`,
   `allSemesterMarks`, `allSemesterExams`, `examSchedule`, `timetable`) are
   removed; consumers read `AppState.academic` (or `loadPersistedSnapshot()`
   in process contexts) directly. No backward-compatible projections.
3. **Curriculum**: stays a sibling catalog (`CurriculumRes` unchanged), not
   nested into the grand schema. Basket items may later be referenced by code.
4. Selected semester (`_selectedSemester`) and selected exam semester
   (`_selectedExamSemester`) remain local `AppState` flows (never stored in the
   snapshot).

## Non-goals (this pass)

- Per-slot venue breakdown (one venue per course, matching current display).
- Semester display names (transport payloads don't reliably carry them; UI
  keeps rendering semester codes).
- Server-side changes (AmazeCC-API transport shapes are untouched).
- Curriculum basket ↔ course-code cross-linking.

## Success criteria

- `:shared:compileAndroidMain` clean after the rewrite.
- Export/import round-trip: v2 snapshot exports and re-imports losslessly;
  v1-format backup files still import (migrated).
- v1 → v2 in-place migration on existing installs (restore path) with zero
  refetch required.
- Grep-verified zero references to the removed flows/setters.
- Snapshot JSON contains exactly one `StoredCourse` per (sem, code) per field
  set; no `attendance`/`marks`/`examSchedule` mirror keys.

## Docs in this folder

| File | Contents |
|---|---|
| `01-overview.md` | this file |
| `02-target-schema.md` | the unified models, merge rules, derivation helpers, model lifecycle (deleted / transport-only / stored) |
| `03-migration.md` | snapshot versioning, v1→v2 mapping, backup compatibility, failure fallback |
| `04-consumer-rewrite.md` | exhaustive consumer inventory + the new read patterns per file |
| `05-store-and-sync.md` | AppDataStore API and AppState sync rewiring spec |
| `06-verification.md` | compile/test/manual verification matrix + risks |
