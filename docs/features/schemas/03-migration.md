# Unified Academic Schema — Migration

## Snapshot versioning

`AppDataSnapshot.schemaVersion: Int = 2`:
- **v2** (new): has `academic: AcademicData`.
- **v1** (legacy): the current shape — no `schemaVersion` (defaults are not
  trusted for detection), 8 academic fields present, `academic` absent.

Detection is by content, not by field default: decode the raw JSON with
`ignoreUnknownKeys` into BOTH candidate shapes and check which one fits:
if the raw object contains an `"academic"` key → v2; otherwise → v1.

To keep this clean, the legacy shape stays in the codebase as
`state/LegacySnapshot.kt` (`@Serializable LegacyAppDataSnapshot`), used ONLY by
the migration path (and by old-backup import). It is not a state flow and is
never written.

## v1 → v2 mapping (`state/SnapshotMigrator.kt`, pure)

| v1 field | v2 target |
|---|---|
| `attendance: AttendanceRes?` | `academic.semesters[attendance.semesterId ?: <selected-at-migrate-time>]` ← `upsertAttendance` |
| `allSemesterAttendance: Map<sem, AttendanceRes?>` | per sem ← `upsertAttendance` |
| `marks: MarksRes?` | semester key: `_selectedSemester` is NOT available during `restore()` — see below |
| `allSemesterMarks: Map<sem, MarksRes>` | per sem ← `upsertMarks` |
| `allGrades: AllGradesRes?` | per sem ← `upsertGrades` (gpa + grade blocks) |
| `examSchedule: ExamScheduleRes?` | `semesters[<selected>]` ← `upsertExams` — dropped if no key matches (v1 mirror of allSemesterExams; ignore) |
| `allSemesterExams: Map<sem, ExamScheduleRes?>` | per sem ← `upsertExams` |
| `timetable: TimetableRes?` | `semesters[<selected>]` ← `upsertTimetable` — ignored if key matches nothing |
| `curriculum` + all other modules | copied through unchanged |

**Selected-semester ambiguity in v1 mirrors**: the v1 single-semester fields
(`attendance`, `marks`, `examSchedule`, `timetable`) are mirrors of
`allSemester*` entries keyed by the user's selected semester at save time,
which is not recoverable from the snapshot. Mapping rule: upsert the v1 mirror
into the semester that best matches —
1. if `allSemester*` contains a semester whose serialized content equals the
   mirror (byte-equal), skip the mirror (already covered); otherwise
2. if exactly one semester in `allSemesterAttendance` has a non-empty
   `attendance` list not equal to the mirror, treat the mirror as the
   "current" semester → key = the only semester with attendance data
   (`attendance` is fetched only for the selected semester during full sync);
   else
3. keep the mirror as the FIRST upsert into an empty-`academic` fallback
   semester keyed `"__v1_mirror"` — last resort, never silent data loss, and
   refetch on next sync overwrites it.

In practice rule 2 is what fires: today `attendance` is fetched for the
selected semester only, and `allSemesterAttendance[selected]` holds the same
object, so the byte-equality check in rule 1 already resolves it.

## Migration flow in `AppDataStore.restore()`

1. Read + decrypt `CACHE_APP_DATA` (unchanged).
2. Peek raw JSON for `"academic"`:
   - present → decode v2, sanitize with the normal setters, done.
   - absent → decode `LegacyAppDataSnapshot`, run `SnapshotMigrator.toV2()`,
     `persist()` the migrated snapshot **in the same restore pass**, then
     proceed as v2.
3. If decode fails entirely (corrupt/old key format): fall back to
   `migrateLegacyCaches()` (existing per-key migration, now writing into
   `AcademicData` instead of the 8 fields) — and if that finds nothing,
   start empty (refetch on next sync). No crash path.

## Backup compatibility (`ExportImportManager`)

- **Export**: `AppDataStore.exportSnapshot()` now serializes the v2 snapshot
  (with `academic`). The `appData` backup field gains `schemaVersion`.
- **Import of v2 backup**: decode normally.
- **Import of v1 backup** (older app version exported it): the `appData`
  string is decoded as `LegacyAppDataSnapshot` → `SnapshotMigrator.toV2()` →
  `AppDataStore.importSnapshot(migrated)`.
- **Import of pre-appData backups**: legacy `cache_*` settings entries are
  still applied (existing behavior); the next `restore()` migrates them into
  the store.

## Legacy per-key cache migration (`migrateLegacyCaches`)

Re-targeted writers (previously `setAttendance`/`setAllSemesterMarks`/...):

| Legacy key | v2 write |
|---|---|
| `CACHE_ATTENDANCE` | `academic.semesters[res.semesterId]` ← upsertAttendance |
| `CACHE_ALL_SEMESTER_ATTENDANCE` | per sem ← upsertAttendance |
| `CACHE_MARKS` | `academic.semesters[<fallback>]` ← upsertMarks (same mirror-rule as above, keyed by the AttendanceRes semesterId when present, else rule 2) |
| `CACHE_ALL_SEMESTER_MARKS` | per sem ← upsertMarks |
| `CACHE_EXAM_SCHEDULE` | skip if covered by allSemesterExams (mirror) |
| `CACHE_ALL_SEMESTER_EXAMS` | per sem ← upsertExams |
| `CACHE_GRADES` / `CACHE_ALL_GRADES` | per sem ← upsertGrades |
| `CACHE_TIMETABLE` | ← upsertTimetable |
| `CACHE_QCM_VIEW`, rest | unchanged behavior |

All 28 legacy keys are still deleted after read (unchanged).

## Data-loss protections

- Migrator is pure + unit-testable (byte-equality mirror resolution is
  explicitly tested).
- No domain is dropped silently: every v1 field has a target.
- Post-migration, next sync upserts replace/refresh each domain — a wrong
  semester-key guess self-heals on the first full sync.
- Backup import and in-place restore share the same migrator (single code
  path, no drift).
