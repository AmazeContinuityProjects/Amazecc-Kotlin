# Unified Academic Schema — Target Schema

## New stored models (`state/AppModels.kt`)

```kotlin
/** All academic data, one semester map. Replaces the 8 academic AppDataSnapshot fields. */
@Serializable
data class AcademicData(
    val semesters: Map<String, SemesterData> = emptyMap()   // semesterId -> data
)

@Serializable
data class SemesterData(
    val semesterId: String = "",
    val semesterName: String? = null,        // transport payloads rarely carry it; usually null (see 01-overview non-goals)
    val gpa: String? = null,                 // from the grades payload (cleaned)
    val courses: Map<String, StoredCourse> = emptyMap(),   // courseCode -> merged course
    val exams: List<ExamItem> = emptyList()  // exam INSTANCES for the semester (dates/sessions/seat)
)

/** One course in one semester — the single source of truth. */
@Serializable
data class StoredCourse(
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",             // "Theory Only" | "Lab Only" | "Embedded Theory and Lab" | ...
    val category: String? = null,            // "University Core Courses" | "Programme Core Courses" | ...
    val credits: String? = null,             // attendance `credits` or timetable `LTPJC` (cleaned)
    val classId: String? = null,             // from timetable courseInfo
    val slots: List<String> = emptyList(),   // slot codes, e.g. ["C2", "TC2"]
    val venue: String? = null,               // single venue (timetable slotVenue or attendance slotVenue)
    val faculty: String? = null,
    val courseSystem: String? = null,        // from marks (`Regular`/`Distance`...)
    val attendance: StoredAttendance? = null,
    val marks: StoredMarks? = null,
    val grade: StoredGrade? = null
)

@Serializable
data class StoredAttendance(
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0,
    val attendancePercentage: String = "",   // cleaned number string ("93"), like today
    val logs: List<AttendanceLog> = emptyList()   // existing AttendanceLog(date, status)
)

@Serializable
data class StoredMarks(
    val classNbr: String? = null,
    val assessments: List<AssessmentItem> = emptyList()   // existing AssessmentItem
)

@Serializable
data class StoredGrade(
    val grandTotal: String? = null,
    val grade: String? = null,
    val details: List<GradeBreakdown>? = null,   // existing GradeBreakdown
    val range: GradeRange? = null                // existing GradeRange
)
```

`AppDataSnapshot` changes:

```kotlin
@Serializable
data class AppDataSnapshot(
    val schemaVersion: Int = 2,          // NEW — 1 = legacy shape (see 03-migration)
    val academic: AcademicData = AcademicData(),   // REPLACES the 8 fields below
    // ...all other modules unchanged (hostel, mess, calendar, qcm, curriculum, ...)
)
```

**Removed snapshot fields** (replaced by `academic`): `attendance`,
`allSemesterAttendance`, `marks`, `allSemesterMarks`, `allSemesterExams`,
`examSchedule`, `allGrades`, `timetable`.

## Model lifecycle

| Model | Fate |
|---|---|
| `AcademicData`, `SemesterData`, `StoredCourse`, `StoredAttendance`, `StoredMarks`, `StoredGrade` | NEW — stored |
| `AttendanceLog`, `AssessmentItem`, `GradeBreakdown`, `GradeRange`, `ExamItem` | kept — reused inside stored models |
| `AttendanceRes`, `AttendanceItem` | TRANSPORT-ONLY (API decode; never persisted) |
| `MarksRes`, `MarksCourseItem` | TRANSPORT-ONLY |
| `AllGradesRes`, `SemesterGradeResult`, `GradeItem` | TRANSPORT-ONLY |
| `ExamScheduleRes` | TRANSPORT-ONLY (the `schedule` map is flattened into `SemesterData.exams`) |
| `TimetableRes`, `TimetableCourseInfo` | TRANSPORT-ONLY |
| `TimetableSlot` | kept — the DERIVED weekly-view type (built from `SemesterData`) |
| `CurriculumRes` + basket models | UNCHANGED sibling catalog |
| `AttendanceItem.logs` / `viewLinkRaw` | transport stays as-is; stored representation moves to `StoredAttendance.logs` |
| `QcmTable`/`StoredQcmTable` etc. | unchanged |

## Merge rules (upsert semantics, pure functions — new `state/AcademicMerge.kt`)

All upserts sanitize at the store boundary (existing `AppSanitizers` cleaners
are reused on transport fields before they land in `StoredCourse`).

| Upsert | Behavior |
|---|---|
| `upsertAttendance(sem, AttendanceRes?)` | For each `AttendanceItem` (valid code only): set `attendance` block on the course. Attendance is a complete per-sem server list → **replace** the block. Courses without a code are dropped (existing `isValidCourseCode`). |
| `upsertMarks(sem, MarksRes?)` | Replace `marks` block per course (complete list semantics). `cgpa` of `MarksRes` is NOT stored (per-sem GPA lives in `SemesterData.gpa` from grades; the transport `cgpa` result stays a UI-side calc for the marks tab). |
| `upsertGrades(AllGradesRes?)` | Per semester: set `gpa` + replace `grade` block per course. |
| `upsertExams(sem, ExamScheduleRes?)` | Flatten `schedule.values` → replace `sem.exams` (complete list). |
| `upsertTimetable(sem, TimetableRes?)` | **Identity merge**: for each `courseInfo` row (valid code): fill `courseTitle` (cleaned), `courseType` (from `(L)`/`(T)` suffix or category contains "Lab"), `category`, `credits` (LTPJC), `classId`, `slots` (resolved from slotVenue via `splitSlotVenue`), `venue`, `faculty` — **without touching** existing `attendance`/`marks`/`grade` blocks. Missing/empty fields are filled, never blanked. |

Course-identity merge across domains: first non-empty wins per field;
`slots` merges by union when both timetable and attendance contribute
(timetable slotVenue is authoritative, attendance `slotName` is the fallback —
same precedence as today's `buildTimetableSlots`).

Deletion semantics: a domain upsert that arrives empty (e.g. `marks` payload
with no courses) replaces that domain with nothing for courses it previously
covered — matching today's behavior of replacing the stored `MarksRes`.

## Derivation helpers (pure, `state/AcademicDerivers.kt` or utils)

These replace the old "projection" idea only where a shared shape is still
useful to multiple consumers:

- `buildWeeklyTimetable(sem: SemesterData): List<TimetableSlot>` — slots ×
  `SlotMap` → day/time with attendance % from `course.attendance.percentage`.
  Replaces `AppSanitizers.buildTimetableSlots` (which is deleted from the
  sanitizer; the slot-resolution helpers `slotIndex`, `splitSlotVenue`,
  `courseTypeOf`, `cleanCourseCode` move here unchanged).
- `percentOf(course: StoredCourse): Double?` — from `attendance.percentage`.
- `computeODHours(sem)` — from `course.attendance.logs` (existing logic moved
  from `WidgetDataUtils`).

## Example (from the real backup, after migration)

```
semesters["CH20262701"].courses["BACSE105(T)"] = StoredCourse(
    courseCode = "BACSE105(T)", courseTitle = "Data Structures and Algorithms",
    courseType = "Embedded Theory", category = "Programme Core Courses",
    credits = "3.0", classId = "CH20262701020...", slots = ["F1","TF1"],
    venue = "AB1-609", faculty = "52262 MERCY RAJASELVI BEAULAH P SCOPE",
    attendance = StoredAttendance(attendedClasses=12, totalClasses=13,
        attendancePercentage="93", logs=[AttendanceLog("05-Aug-2026","Present"), ...]),
    marks = ..., grade = ...)
```
