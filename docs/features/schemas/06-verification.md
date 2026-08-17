# Unified Academic Schema — Verification

## Compile gates (Windows, run after each phase)

```
.\gradlew.bat :shared:compileAndroidMain --console=plain -q
```

Phases:
1. models + merge/derivers + migrator compile standalone
2. store API swap compiles (consumers broken at this point BY DESIGN — only
   `AppState`/store internals compile; run after store+AppState rewiring)
3. Tier 1–3 consumers ported → full compile clean (target: zero academic-flow
   references)

## Grep gates

```
# no leftovers of the old API anywhere
AppState\.(attendance|marks|allGrades|allSemesterAttendance|allSemesterMarks|allSemesterExams|examSchedule|timetable)\b
AppDataStore\.(setAttendance|setMarks|setAllGrades|setAllSemesterAttendance|setAllSemesterMarks|setAllSemesterExams|setExamSchedule|setTimetable)\b
# allSemester maps must be gone from AppState write sites
```

## Unit tests (commonTest — pure functions; run on macOS/CI)

| Test | Covers |
|---|---|
| `AcademicMergeTest` | attendance/marks/grades/exams/timetable upsert replaces only its domain; identity merge fills don't clobber; slots union precedence; `(T)`-garbage dropped; empty-domain replacement |
| `SnapshotMigratorTest` | v1→v2: each v1 field lands; mirror-resolution rules 1–3 (byte-equal skip / single-sem match / `__v1_mirror` fallback); null semesters; round-trip v2→JSON→v2 |
| `AcademicDeriversTest` | `buildWeeklyTimetable` matches old sanitizer output on fixture data; `computeODHours`; percent parsing |
| `LegacyCacheMigrationTest` | each legacy key → correct semester/course placement; keys deleted after read |

Same platform caveat as before: no jvm target → compile-only on Windows;
`iosSimulatorArm64Test` on macOS/CI.

## Manual matrix (device, after build)

| # | Scenario | Expected |
|---|---|---|
| 1 | Fresh login → full sync | All tabs render; course counts match VTOP; dashboard metric cards correct |
| 2 | Existing install (v1 snapshot) → update → open app | No refetch required; all screens correct immediately (migrated in restore) |
| 3 | Attendance % / CGPA predictor | Numbers match pre-migration values exactly |
| 4 | Course dashboard / course detail (join-heavy) | Per-sem grades/attendance/marks correct; multi-sem course shows all occurrences |
| 5 | Exam schedule + exam cards + seat alerts | Same data as before, per selected exam semester |
| 6 | Widgets (next class, OD hours, today's classes) | Refresh shows correct data (process reads `loadPersistedSnapshot`) |
| 7 | Notification reschedule after exam change | Fires against migrated snapshot |
| 8 | Full backup export → wipe → import | Restores all academic data; JSON contains `academic`, no mirror keys |
| 9 | Old-format backup (v1 `appData`) import | Migrates correctly, no data loss |
| 10 | Logout → login | `academic` cleared, fresh sync repopulates |
| 11 | Semester switch | All selected-sem views switch; no stale mirrors |
| 12 | OD tracking screen | Logs render from `course.attendance.logs`; OD hours correct |

## Risks

| Risk | Mitigation |
|---|---|
| v1 mirror semester-key guess wrong | Pure migrator + byte-equality rule; self-heals on next full sync (no data loss) |
| Consumer rewrite misses a read site | Grep gates (above) + compile; every file in `04-consumer-rewrite.md` checked off |
| `buildWeeklyTimetable` output drifts from old sanitizer | Fixture test comparing old vs new on the same input |
| Grade-only semesters break gap-fill | Invariant #4 in `05-store-and-sync.md`; unit test |
| Snapshot size regresses | Dedup should shrink; compare export byte size v1 vs v2 on the same device |
| Widget process reads stale snapshot | Already mitigated — `loadPersistedSnapshot()` per read |
| Tests can't run on Windows | macOS/CI as before |
