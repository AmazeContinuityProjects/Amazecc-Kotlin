# Data + translation layer — design

Status: design agreed, not yet implemented. Written before any code so the seams are explicit.

## The problem, as found

`AppDataSnapshot` (`AppModels.kt:168`) is a god object with ~26 fields, and it holds data in **two
parallel worlds**:

1. `academic: AcademicData` — the one genuinely unified shape.
   `semesters[id].courses[code]` is a `StoredCourse` that already carries
   `attendance`, `marks`, `grade`, `slots`, `venue`, `credits`.
2. Everything else as a **raw transport DTO**: `qcmView`, `curriculum`, `payments`, `lms`,
   `events`, `registeredEvents`, `circulars`, `messMenu`, `library`, `transportData`, `buses`, …

Consequences, all of which are the "multiple dump storage points" problem:

- A screen that needs attendance reads the unified shape; a screen that needs CGPA or curriculum
  reaches into a transport DTO. Every transplanted screen picks whichever is closer, so mapping
  logic accretes in the UI instead of in one place.
- The same concept is representable twice. `StoredCourse.credits` and
  `CurriculumRes.categories[].credits` are different shapes for the same fact, and nothing
  reconciles them.
- `AppSanitizers` already does translation, but it is applied *per field at the store boundary*
  (`sanitizeQcmView`, `sanitizeHostelDetails`, …). A screen needing two modules still has to
  merge them itself.

## The three layers

```
transport DTOs            ← what VTOP/EventHub/Moodle parsers return
      │
      ▼
  Ingestor                ← one entry point, DTO → domain
      │
      ▼
  DomainSnapshot          ← one canonical shape per concept, no transport types
      │
      ▼
  Projections             ← pure DomainSnapshot → screen view models
      │
      ▼
  Compose screens         ← read projections only, never a DTO
```

### 1. Ingestor

`VtopIngestor.ingest(dtos: TransportBundle): DomainSnapshot`

Single entry point, called once per sync. Replaces the per-field sanitizer calls. The bundle is
whatever the sync produced; the ingestor's job is to fold overlapping DTOs into single domain
objects and drop anything already superseded.

Rules that matter:

- **No transport type escapes.** `DomainSnapshot` must not reference `*Res` DTOs. If a domain
  model needs a field the DTO has, copy the value; do not keep the DTO.
- **One concept, one home.** Credits arrive from timetable *and* curriculum. The ingestor picks
  one (curriculum is authoritative — it is the official structure) and records the conflict
  rather than letting a screen pick.
- **Embedded courses resolved once, at ingest.** ETH/ELA merging happens here, keyed on
  `Embedded Theory` / `Embedded Lab` / `Theory Only` / `Lab Only` (the real VTOP vocabulary,
  per the ground-truth audit). `AcademicMerge` already does the weighting; the ingestor decides
  *when*.
- **Idempotent.** A re-ingest with identical input must produce an identical snapshot, so a failed
  module retry cannot corrupt what already succeeded.

### 2. DomainSnapshot

Grouped by concern, not by module — so screens find what they need without knowing which sync
produced it:

```kotlin
data class DomainSnapshot(
    val identity: Identity,
    val academics: Academics,      // semesters → courses → attendance/marks/grade/curriculum
    val schedule: Schedule,        // timetable grid, exams, calendar
    val finance: Finance,          // payments, receipts, wallet, dues
    val campus: Campus,            // hostel, mess, transport, library
    val engagement: Engagement,    // events, registered events, clubs, circulars
    val services: Services,        // lms, qcm, dayboarder, apaar
)
```

`Academics` extends today's `AcademicData` rather than replacing it: `StoredCourse` is already
the right grain (one course in one semester, carrying its own attendance/marks/grade), it just
needs curriculum credits and the corrected ETH/ELA key merged in.

### 3. Projections

Pure functions, no I/O, trivially unit-testable — which is the point, now that `jvmTest` runs.

```kotlin
object Projections {
    fun academics(snapshot, semesterId): AcademicsView
    fun attendance(snapshot, semesterId): List<AttendanceRow>
    fun marks(snapshot, semesterId): List<CourseMarksView>
    fun exams(snapshot, semesterId): List<ExamCard>
    fun curriculum(snapshot): List<CategoryView>
    fun payments(snapshot): PaymentsView
    fun events(snapshot): List<EventCard>
    ...
}
```

Screens take a view model, not a `StateFlow<DomainSnapshot>` and not a DTO. That is what makes
screens transplantable: a transplanted screen depends on a projection, and the projection is
rewritten if the storage shape changes — not the screen.

## Sequencing

1. **Domain models + `DomainSnapshot`** — pure data, no behaviour. Proves the shape compiles.
2. **Ingestor over the existing snapshot** — `DomainSnapshot.from(AppDataSnapshot)`. Read-only
   migration, so it can land without touching the sync engine.
3. **Projections**, one per screen family, each with JVM tests.
4. **Point `AppState` at `DomainSnapshot`**; keep `AppDataSnapshot` for persistence only.
5. **Then** transplant screens, each against a projection.
6. **Then** let the sync engine write domain directly and retire the god object.

Steps 1–3 are additive and reversible. The god object is not deleted until every screen is off it.

## Open questions

- Does `AppDataSnapshot` stay the persisted format, or does the domain become it? Keeping it
  short-lived is safer but means writing a migration later; promoting it avoids the migration but
  invalidates existing installs' caches.
- `identity` already has a home (`UserStore`). Fold it in, or keep it separate and have the
  projection read both?
