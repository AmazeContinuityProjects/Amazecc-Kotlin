# Data + translation layer — implementation log

Companion to `data-translation-layer-design.md`. Decisions taken, what landed, what is next.

## Decisions

| Question | Answer |
|---|---|
| Persisted format | **Promoted.** `DomainSnapshot` becomes the stored format at `schemaVersion = 3`. |
| Identity | **Kept in `UserStore`.** Login-scoped, not sync-scoped; merging would put a nullable copy in every projection. |

## What landed

### Desktop/JVM target (`shared/build.gradle.kts`)

`jvm()` added with `compose.desktop.currentOs`, so desktop runs the *same* `commonMain` as
Android rather than a reduced build — UI code stays honest while iterating.

Six actuals under `shared/src/jvmMain/`:

| File | Behaviour |
|---|---|
| `vtop/VtopEngine.jvm.kt` | throws — desktop falls back to `VtopSource.REMOTE`, same as iOS |
| `vtop/captcha/CaptchaEngine.jvm.kt` | `isReady = false` → always manual entry |
| `vtop/ImageDecoding.jvm.kt` | real decode via ImageIO |
| `utils/DesktopStubs.kt` | notifications/alarms/widgets no-op; file saver **really writes** to `build/desktop-exports` |
| `ui/components/DesktopComponents.kt` | `AppBackHandler` no-op, `LatexViewer` renders source |
| `services/NotificationService.jvm.kt` | prints |
| `security/Encryption.jvm.kt` | AES with a **fixed key** — obfuscation only, not the Android threat model |

Verified: `:shared:compileKotlinJvm`, `:shared:compileAndroidMain` and `:androidApp:compileDebugKotlin`
all pass. The target is additive; nothing on Android regressed.

### `domain/DomainSnapshot.kt`

`DomainSnapshot` grouped by concern — `academics`, `schedule`, `campus`, `engagement`, `services`.

`academics` and `schedule` hold **no transport types**: `Course`, `Attendance`, `Marks`,
`Assessment`, `Grade`, `Exam`, `CalendarMonth` are all modelled here. `Course` carries its own
attendance/marks/grade, so those cannot disagree with each other.

`campus`/`engagement`/`services` still hold some `*Res` DTOs and are marked **PENDING** with the
outstanding work listed. That is deliberate: copying twenty DTO shapes unverified would rebuild
the god object under a new name. Each group migrates when its screen is ready.

### `domain/VtopIngestor.kt`

- `fromLegacy(AppDataSnapshot)` — read-only, reversible bridge so this can land before any screen
  moves.
- **Curriculum wins on credits.** It is the official programme structure; the timetable's LTPJC
  column is a formatting convenience that parses unreliably. A conflict is resolved once, here.
- `merge(current, incoming)` under a `Mutex` — incoming semesters win, anything missing is carried
  forward, so a retried module cannot blank good data. The policy is what the old sync engine
  already called "never persist nulls over good data".

### `domain/Projections.kt`

Two kinds of function now live here.

**Snapshot projections** — pure `DomainSnapshot -> view model`. Screens take these, never the
snapshot and never a DTO. `isLabType` matches the **real** strings — `Embedded Lab`, `Lab Only`,
`lab`, `ELA` — and deliberately does *not* invent a match for `Embedded Theory`. The audit found the
port normalising against `ETH`/`ELA`/`Theory Only`/`Lab Only`, which is why the credit-weighted
merge never engaged.

**Primitive formulae** — the canonical implementations of facts that used to be written out inline
wherever they were needed. These are the ones both the legacy layer (`state/`, `utils/`) and the
new domain layer call, so two surfaces cannot disagree about the same figure:

- `summariseAttendance(courses, targetPct)` / `attendanceStatus` / `AttendanceSummary` — the app's
  one attendance percentage: an **unweighted** sum of attended/total, computed in double precision
  and narrowed to `Float` once. `DEFAULT_ATTENDANCE_TARGET` exists only so a caller that reads only
  the figure can omit the target; `attendanceStatus`'s band is five points wide (`>= target+5` →
  SAFE, `>= target` → WARNING, else CRITICAL, and no held classes beats every band).
- `attendanceSummary(snapshot, semesterId, targetPct)` — the same, over a snapshot's semester.
- `isOdStatus(status)` — VTOP's three spellings (`on duty`, `od`, `onduty`) and nothing else;
  `partial od` and `sectional holiday` must not count, because OD hours are what buys a bunk.
- `odHours(sessions)` — a lab period is two hours, a theory period one. The OD Tracker counter,
  which every other OD-hours surface now calls.
- `creditsEarned(courses)` — takes `(credits, hasGrade)` pairs rather than a snapshot type, so the
  two legacy call sites share it without the interpretation layer depending on the store.

## The interpretation layer, and what it collapsed

Six Kotlin implementations of the headline attendance percentage existed. `HomeModels` had the
right one but was trapped in the home package; `DashboardWidgets` had three inline copies;
`WidgetDataUtils` had a fourth; and `AcademicsScreen` computed the **mean of the per-course
percentages** — the exact bug the web app shipped on one of its five copies. A course with no held
classes dragged that headline down, and a one-class course counted as much as a forty-class one.
All six now call `Projections.summariseAttendance`.

Two OD-hour counters also disagreed about lab detection: `WidgetDataUtils.computeODHours` only
tested `slotName.startsWith("L")`, while `AcademicDerivers.computeODHours` also read the `(L)` code
suffix. A lab whose slots did not start with `L` was 1 hour in a widget and 2 on the home screen.
Both now route through `AcademicDerivers.isLabCourse(code, type, slots)`, `Projections.isOdStatus`
and `Projections.odHours`. Three separate lab tests collapsed into that one.

`creditsEarned` had two byte-identical copies (`AcademicsScreen`, `WidgetDataUtils`); both call
`Projections.creditsEarned`.

**`AppState.domain`** (Step 0) exposes the bridge screens migrate onto:

```kotlin
val domain: StateFlow<DomainSnapshot> = combine(AppDataStore.data, selectedSemester) { snapshot, semester ->
    VtopIngestor.fromLegacy(snapshot, semester)
}.stateIn(scope, SharingStarted.Eagerly, VtopIngestor.fromLegacy(AppDataStore.data.value, selectedSemester.value))
```

`combine` rather than `map`, so it also reacts to the semester changing. No new persistence, no
second copy on disk. Nothing reads it yet — the first screen migration is Wave 1.

**Design system relocated.** `HomePrimitives.kt` + `HomeTokens.kt` moved to `ui/design/` (`git mv`),
and `HomeInsightSlide` moved out of `HomeModels.kt` into `HomePrimitives.kt`, next to the
`HomeInsightCarousel` that consumes it — the design layer had been depending on a screen's model
type. `internal` was deliberately **kept**: an earlier draft of the plan claimed `internal` blocked
use outside `ui/screens/home/`, but Kotlin scopes `internal` to the *module*, and there are 54
cross-package `internal` usages in `commonMain` already. The only fix needed was 4 wildcard imports.


## Tests

`commonTest/.../domain/ProjectionsTest.kt` — 19 tests. They pin the behaviours the
audit exposed:

- embedded halves collapse to one bare course code
- curriculum beats timetable for credits
- ingest is idempotent
- `Embedded Theory` is **not** a lab, `Lab Only` **is** — the exact regression that broke the merge
- marks expose the weighted total, trimmed (`9.0` → `9`)
- a missing semester yields empty projections rather than throwing

plus the new formulae:

- headline attendance is the unweighted sum, never an average of percentages (1/1 + 9/99 is 10%,
  not the 54.5% averaging gives)
- a course with no held classes contributes nothing rather than dragging the figure down
- no held classes reads as zero with an N-A status, not as 0% attendance
- the status bands are exactly five points wide at the target
- the percentage is computed in double precision
- the domain summary matches the same figure computed by hand (58/64 = 90.625%)
- credits earned counts only courses with a posted grade
- the OD status vocabulary accepts VTOP's three spellings and nothing else
- a lab OD session is two hours, a theory session one

`commonTest/.../state/AcademicDeriversTest.kt` - 3 more, covering the OD collapse: a lab whose
code ends in `(L)` but whose slot is not lab-shaped counts as 2 hours **and both entry points
return the same number**, theory/lab weighing, and statuses outside the vocabulary counting for
nothing.

The stale-test blocker below is **resolved**; the suite runs on `:shared:jvmTest`. **224 -> 236,
all passing.**

## `DomainSnapshot` is now the persisted format

`AppDataStore` writes v3 and reads all three schemas. The in-memory shape is still
`AppDataSnapshot`, deliberately: ~43 screens read those flows today, and `VtopIngestor.toLegacy` is a
proven-lossless inverse. When the last screen moves to a projection, `_data` becomes the domain
snapshot and both bridge functions are deleted.

### Making the conversion provably lossless came first

Promoting the snapshot as it stood would have been a data-loss bug. `fromLegacy` mapped 13 of
`AppDataSnapshot`'s 22 top-level fields and silently defaulted the rest, so payments, laundry,
counselling, calendars-list, curriculum, clubs, Moodle, tasks, cab-share and FFCS registration would
all have been **deleted on upgrade** with no error.

So the shape was widened first until it was a superset, then the property was pinned by a test
rather than by inspection: `SnapshotRoundTripTest` populates every field with a sentinel and asserts
the whole set survives. Adding a field to `AppDataSnapshot` without giving it a home in
`DomainSnapshot` now fails the build.

Fields that had been dropped from inside nested types are covered too — `StoredCourse.courseSystem`,
`ExamItem.classId`/`slot`, `AssessmentItem.status`, and `GradeBreakdown.status`/`weightageMark`.
`GradeRange` became a structured `GradeBands` rather than the pre-rendered
`"S 90 / A 80 / …"` string, because a screen that wants to test a total against a band cannot do
that from a string.

One intentional normalisation: the `CalendarRes` envelope (`success`/`error`/`message`) is
reconstructed as `success = true` on the way back, since a synced calendar with months is always a
success. Null transport fields stay null; only defaults are written.

### Version detection

`SnapshotCodec` discriminates by marker, checked in order v3 → v2 → v1:

| version | shape | marker |
|---|---|---|
| 3 | `DomainSnapshot` | `"academics"` |
| 2 | `AppDataSnapshot` | `"academic"` |
| 1 | `LegacyAppDataSnapshot` | neither |

`"academic"` cannot match inside `"academics"` — the closing quote is what stops it.

The order is load-bearing and is itself tested: `kotlinx.serialization` defaults unknown fields, so
decoding a v3 blob as v2 would *succeed* into an empty snapshot. That is a silent wipe, not a crash.
`SnapshotCodecTest.decodingAV3BlobAsV2WouldProduceAnEmptySnapshotWhichIsWhyOrderMatters` pins it.

### A bug the empty-snapshot test caught

With `encodeDefaults = false`, an all-default snapshot omitted `academics` entirely — so a fresh
install wrote a **v3 blob carrying no v3 marker**, which read back as v1 and took the legacy
migration path. Harmless only while that migrator returns empty for empty input. The encoder now
always writes its markers, while nulls are still omitted, so absent DTOs don't bloat the blob.

### Backup is deliberately unchanged

`exportSnapshot()` still emits v2. A backup file is a long-lived external contract with its own
`BackupFile.formatVersion`; switching its payload would make every backup already on a device
unreadable. Import re-encodes as v3 on the next persist.

### Two mapping bugs fixed while doing this

* `selectedSemesterId` was `semesters.keys.maxByOrNull { it }`. Semester ids are
  `CH20262701` / `CH20252601` / `CH20222323`, so the lexicographically greatest is the **oldest**
  semester — the app opened on a semester from years ago. Now compares year then term, and the
  user's own choice (`KEY_SELECTED_SEMESTER`) is persisted with the snapshot instead of re-guessed
  on every read.
* `Exam.section` was documented as the exam's own section header but was assigned the semester id,
  which is what `Projections.exams` filters on. Renamed to `semesterId` so the doc and the code
  cannot drift apart again.

## Next

1. Transplant screens, each against a projection.
2. Let the sync engine write the domain snapshot directly; delete `fromLegacy`/`toLegacy` and the
   god object.
3. Replace the remaining transport DTOs in `Campus`/`Engagement`/`Services`, one screen at a time.
4. Runnable Desktop entry point.

## Ground truth

`docs/sep-30-2026/vtop-ground-truth-audit.md` — what VTOP actually serves, from 126 scraped pages
(`../vtop-snapshots/`, gitignored).
