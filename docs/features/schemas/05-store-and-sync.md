# Unified Academic Schema — Store & Sync

## AppDataStore API (`state/AppDataStore.kt`)

### Removed (8 fields + their flows/setters)

- Flows: `attendance`, `marks`, `allGrades`, `allSemesterAttendance`,
  `allSemesterMarks`, `allSemesterExams`, `examSchedule`, `timetable`.
- Setters: `setAttendance`, `setMarks`, `setAllGrades`, `setAllSemesterAttendance`,
  `setAllSemesterMarks`, `setAllSemesterExams`, `setExamSchedule`, `setTimetable`.

### Added

```kotlin
val academic: StateFlow<AcademicData> = derived { it.academic }

fun upsertAttendance(semesterId: String, res: AttendanceRes?)   // sanitize → AcademicMerge.upsertAttendance
fun upsertMarks(semesterId: String, res: MarksRes?)
fun upsertGrades(res: AllGradesRes?)                            // all semesters from the payload
fun upsertExams(semesterId: String, res: ExamScheduleRes?)
fun upsertTimetable(semesterId: String, res: TimetableRes?)     // identity merge; % filled from academic's own attendance
fun updateSemester(semesterId: String, transform: (SemesterData) -> SemesterData)  // escape hatch for future modules
```

All upserts run `update { s -> s.copy(academic = ...) }` — the existing
equality-guard + encrypted `persist()` pipeline is unchanged.

`sanitize*` helpers stay in `AppSanitizers` (they clean transport fields
before the merge); `buildTimetableSlots`/`slotIndex`/`splitSlotVenue`/
`courseTypeOf`/`cleanCourseCode` MOVE to `AcademicDerivers` (kept public,
still used by consumers that need the weekly view).

### Snapshot / restore

- `restore()`: v1/v2 dual-decode + `SnapshotMigrator.toV2()` (see
  `03-migration.md`), then normal sanitization.
- `migrateLegacyCaches()`: re-targeted writers (see `03-migration.md`).
- `exportSnapshot()` / `importSnapshot()`: v2 shape; import handles v1
  `appData` via the migrator.

## AppState rewiring (`state/AppState.kt`)

### New/changed surface

```kotlin
val academic: StateFlow<AcademicData> = AppDataStore.academic
// selectedSemester / selectedExamSemester / selectSemester / selectExamSemester: unchanged
// openCurriculumCourse: unchanged (curriculum sibling)
```

Removed flows: `attendance`, `marks`, `allGrades`, `allSemesterAttendance`,
`allSemesterMarks`, `allSemesterExams`, `examSchedule`, `timetable`.

### Write-site conversion table

| Site (line) | Today | After |
|---|---|---|
| 881 | `setExamSchedule(allSemesterExams.value[semesterId])` (mirror) | DELETE |
| 1810–1811 | mirror + empty check | DELETE mirror; empty check → `academic.semesters[sel]?.exams?.isEmpty()` |
| 910, 1039, 1319, 1462, 1536, 2449 | `setAllSemesterAttendance(map + (sem to it.attendance))` | `upsertAttendance(sem, it)` |
| 913, 1042, 1322, 1465, 1539, 2452 | `setAllSemesterMarks(map + (sem to marks))` | `upsertMarks(sem, marks)` |
| 1131, 1145, 1849, 1859, 2487 | `setAllSemesterExams(map + (sem to it))` | `upsertExams(sem, it)` |
| 1055–1060 (newAttMap/newMarksMap bookkeeping) | manual map merge for gap-fill | DELETE — gap-fill logic reads `academic.semesters` directly |
| 1309–1322, 1497–1509, 1527–1539 (gap-fill) | `gradeSemIds` vs `allSemesterMarks.keys`/`allSemesterAttendance.keys` | `academic.semesters.keys` (a semester exists once ANY domain landed; gap-fill fetches attendance+marks for grade-only semesters — same condition, new map) |
| 1355 | `attendance.value?.attendance` (sync-list refresh logic) | `academic.semesters[selected]?.courses?.values` |
| 1361–1363 (exam reminders/alerts) | `allSemesterExams.value[...]` | `academic.semesters.values.flatMap { it.exams }` (+ selected-exam-semester filter preserved) |
| 524 | `allGrades.value?.grades` | `academic.semesters` (CGPA/percent derivation) |

### Sync functions touched (same set as the write sites)

`loadSemesterData`, `loadAllData`, `refreshCurrentSemester`,
`refreshAllAcademic`, `refreshPastSemesters`, `runLightReload`,
`syncOnboardingAttendance/Timetable/Grades/Exams` (name-adjusted),
`refreshExamSchedule`, `scheduleReminders`, `reconcileExamSeatAlerts`
(reads side), `updateAttendance`/`updateMarks` (if still present).

## Behavior invariants (must hold after rewiring)

1. `refreshExamSchedule` for the selected sem upserts into
   `semesters[selectedSemester]` — no mirror field to keep in sync.
2. Selected-semester reads and all-semester reads never diverge (one source).
3. `buildWeeklyTimetable` on an upserted semester is byte-identical to today's
   sanitizer-derived `timetable.slots` for the same input.
4. A grade-only semester (attendance/marks not yet fetched) still appears in
   `semesters` with `gpa` + `grade` blocks — gap-fill conditions unchanged.
5. `isValidCourseCode` filtering applies in every merge (no `(T)`-garbage rows).
