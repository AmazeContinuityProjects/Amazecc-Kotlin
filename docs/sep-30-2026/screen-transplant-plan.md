# Screen transplant plan

How the remaining screens get built, in what order, and against which layers.

Companion to `data-translation-layer-design.md` (the architecture) and
`data-translation-layer-implementation.md` (what has landed so far).

---

## 1. Scope, and what "transplant" actually means here

The Node app (`../AmazeCC`) is the reference implementation. The Kotlin app already contains
**64 screens / 19,290 lines** under `ui/screens/`, and the home screen plus its primitive set were
ported from Node earlier. So this is **not** a greenfield port. It is two distinct jobs that have
been conflated:

| job | what it means | size |
|---|---|---|
| **A — layer extraction** | Existing Kotlin screens keep their layout but stop reading transport DTOs; their merging/derivation moves into pure functions | ~40 screens touched |
| **B — fidelity port** | Where Kotlin diverged from Node's behaviour, bring it back in line | a subset, per screen |

Job A is the bulk and it is mechanical once the layers exist. Job B is judgement, and Node is the
arbiter because it has 51 test files and 20+ extracted pure-logic modules that Kotlin does not.

**Screens that already exist and are being *replaced*, not created**, by fidelity order (Kotlin
lines → Node sources):

| Kotlin screen | Kotlin lines | Node sources it consolidates |
|---|---:|---|
| `academics/AttendanceScreen.kt` | 1522 | `AttendanceTabs`, `DailyPlanner`, `DesktopCourseDetail`, `CalendarSubpage` (partial) |
| `academics/CalendarScreen.kt` | 1025 | `CalendarSubpage`, `lib/calendarDay.ts` |
| `academics/CourseDetailScreen.kt` | 1859 | `CourseDetailSubpage` (2140 lines) |
| `academics/CurriculumScreen.kt` | 724 | `CurriculumPage` |
| `academics/GPAPredictorScreen.kt` | 1231 | `MarksPredictorTab`, `CurriculumPage?initialScreen=planner` |
| `academics/GradesScreen.kt` | 406 | `MarksHistoryTab`, `GradesModal` |
| `academics/ExamScheduleScreen.kt` | 423 | `ScheduleDisplay`, `lib/examSchedule.ts` |
| `academics/ODTrackerScreen.kt` | 779 | `ODTrackerSubpage` |
| `academics/CourseAttendanceScreen.kt` | 657 | `CourseDetailSubpage` `-log` / `-predictor` tabs |
| `academics/TasksScreen.kt` | 2417 | `TasksTab`, `lib/tasksStorage.ts`, `lib/taskMatch.ts` |
| `libraries/LibrariesScreen.kt` | 701 | `LibrariesTab`, `DuesView`, `CatalogSearch`, `lib/libraries/koha.ts` |
| `hostel/HostelScreen.kt` | 662 | `HostelOverview`, `MessDisplay`, `LaundryDisplay`, `LeaveDisplay` |
| `events/EventHubScreen.kt` | 766 | `EventHubTab`, `EventHubSubpage` |
| `transport/TransportScreen.kt` | 847 | `BusFinder`, `TransportRegistration` |
| `more/ClubHubScreen.kt` | 295 | `ClubHubTab`, `ClubDetailsModal` |
| `academics/FreeClassroomsScreen.kt` | 802 | `FreeClassroomsTab`, `lib/freeClassrooms.ts` |

Consolidation note: **Kotlin merged several Node screens into one file.** `HostelScreen` covers
four Node subpages; `TasksScreen` is the single largest file in the project. That is not wrong, but
it means a Kotlin "screen" and a Node "screen" are not one-to-one, and review has to be per
*behaviour* rather than per file.

### Not in scope

Node's dead code, confirmed by a static import-graph walk from all 8 `src/app/*` entry points
(261 of 295 files reachable, 34 not):

* `ReelScroller.tsx`, `Toggle.tsx`, `exams/MarksDisplay.tsx` (750 lines, superseded by
  `SimplifiedAcademicsPage` + `CourseDetailSubpage`), `exams/VitolDisplay.tsx` (VITOL never wired),
  four orphan FFCS hooks, `lib/sync-engine/useSync.ts`, and the `shared/*.tsx` re-export shims.
* `activeTab === "dayscholar"` renders `BusFinder` identically to `transport` — a collapsed
  duplicate, already absent from Kotlin's `Screen` enum.
* `activeSubTab === "wishlist"` and `=== "moodle"` are set but never handled in `Dashboard.tsx`, so
  they render a blank panel. **Do not port these two.**
* The legacy alias redirects (`academics:qbank|faculty-info|free-class` → `tools:*`,
  `hostel:payment` → `payments`, `more:qbank` → `tools:qbank`). Kotlin has no aliases; these should
  stay absent.

---

## 2. The three layers

The organising idea: a screen should be a pure function of one view model, and nothing else. Right
now `AppState` is doing all three jobs at once — screens read its flows, call its 60+ action methods,
*and* do their own merging. This plan decomposes it.

```
 transport DTO ──▶ ① data translation ──▶ ② interpretation ──▶ screen
                       (VtopIngestor)        (Projections)         │
                            │                    ▲                │
                            │                    │                │
 persisted snapshot ◀────────┘              ③ functional ◀────────┘
   (SnapshotCodec)                              (Actions)
```

### ① Data translation — DTO → domain

**Lives in** `domain/` (`DomainSnapshot.kt`, `VtopIngestor.kt`) plus `state/AppSanitizers.kt` and
`state/AcademicMerge.kt`.

**Owns:** shape differences between what VTOP sends and what the app means; sanitisation; VTOP
vocabulary normalisation (`Embedded Theory` / `ETH` / `Theory Only` are the same thing to the app
and three different things on the wire); the embedded ETH/ELA credit-weighted merge; non-destructive
merge policy.

**Rules:**
* Pure and total. No I/O, no clock, no settings.
* **Lossless.** `SnapshotRoundTripTest` fills all 23 `AppDataSnapshot` fields with sentinels and
  asserts the set survives. A new field without a domain home fails the build.
* Idempotent — ingesting the same payload twice yields the same snapshot.
* This layer is **done**. Do not add parsing here.

### ② Interpretation — domain → screen view model

**Lives in** `domain/Projections.kt`. **This is where most of the work is.**

**Owns:** filtering, grouping, ordering, and every derived number a human reads — attendance
percentage, OD hours, bunkable margin, credits earned, GPA arithmetic, exam state, week-strip
flavours, calendar enrichment.

**Rules:**
* Pure functions of `(DomainSnapshot, params)`. No clock, no settings, no I/O — `Instant`/`Clock`
  passed in as a parameter, which is why the existing `ExamUtils` takes a `now`.
* **One formula per fact.** The single most important rule in this plan; see §5.
* A projection is the *only* thing a screen binds to. Never a `*Res`, never `StoredCourse`.
* Adding a projection means adding a test that fails if the formula regresses — this is exactly the
  pattern `ProjectionsTest` already establishes.

**Currently:** `Projections` has `activeSemester`, `semester`, `courses`, `attendance`, `marks`,
`exams`, `isLabType`. It is **dead code in production** — nothing outside its own test reads it.
It needs to roughly triple in size.

### ③ Functional — user intent → state change

**Lives in** a new `domain/Actions.kt` (or `usecase/`). **Does not exist yet.**

**Owns:** every command a user can invoke, each with an explicit result type and no UI types.

```kotlin
sealed interface SyncOutcome {
    data class Synced(val modules: List<String>) : SyncOutcome
    data class Failed(val module: String, val reason: String) : SyncOutcome
    data class NeedsCaptcha(val challenge: CaptchaChallenge) : SyncOutcome
    data object Skipped : SyncOutcome
}

object Actions {
    suspend fun sync(scope: SyncScope): SyncOutcome
    suspend fun refreshCurriculum(semesterId: String): SyncOutcome
    suspend fun registerForEvent(eid: String): SyncOutcome
    suspend fun completeTask(id: String, done: Boolean): SyncOutcome
    suspend fun generateFfcsTimetable(choice: FfcsChoice): SyncOutcome
    fun exportBackup(): Result<BackupFile>
    fun clearCache(): Result<Unit>
}
```

**Rules:**
* Takes an explicit `SyncScope` (semester, force, partial) rather than reading global state — that
  is what makes a command testable and reusable from a widget, a notification action, or a screen.
* Returns a sealed result, never throws across the boundary and never writes to a screen field.
* Screens call `Actions.*` and then react to the *result*. They do **not** call `AmazeClient`, do
  not call `AppDataStore` setters, and do not call `SyncEngine` directly.

**Why this matters concretely:** `AppState.kt` currently has **65 `syncModule(...)` call sites** and
is ~2,900 lines. Widget and notification processes must reproduce "load and derive" logic that
screens also re-implement (`NotificationsUtils.kt:357`, `WidgetDataUtils.kt:78`). With a functional
layer, `NotificationService` and a widget both call the same `Actions` + `Interpret` pair.

---

## 3. Step 0 — expose the domain to screens (unblocks everything else)

Today screens can only reach `AppDataSnapshot`, because that is what `AppDataStore` holds in memory.
The domain snapshot exists and is what is persisted, but nothing outside `SnapshotCodec` reads it.

**Status: implemented ✅** (`AppState.kt`, next to `academic`). The bridge reacts to the semester
changing as well as the data changing, which a `map` over `AppDataStore.data` alone would not:

```kotlin
// AppState
val domain: StateFlow<DomainSnapshot> = combine(AppDataStore.data, selectedSemester) { snapshot, semester ->
    VtopIngestor.fromLegacy(snapshot, semester)
}.stateIn(
    scope = scope,
    started = SharingStarted.Eagerly,
    initialValue = VtopIngestor.fromLegacy(AppDataStore.data.value, selectedSemester.value),
)
```

Cost: one pure mapping per update, over a value the store already holds. No new persistence, no
second copy on disk. It converts the screen-facing surface from 23 `StateFlow<*Res>` to one
`StateFlow<DomainSnapshot>`, and makes `Projections` reachable. Nothing reads it yet — the first
screen migration is Wave 1.

Then, per screen: replace `AppState.academic` with `Projections.courses(AppState.domain, sem)`, and
when the last screen is done, `_data` becomes `DomainSnapshot` and
`VtopIngestor.fromLegacy`/`toLegacy` + `SnapshotCodec`'s v1/v2 branches are deleted. The end state is
one type from disk to screen.

---

## 4. Design system port

### Location — done ✅

`HomePrimitives.kt` (840 lines) and `HomeTokens.kt` (549) are **already** a faithful port of Node's
`shared/primitives/**` and `lib/uiTokens.ts` — `HomeTileSurface`, `HomeListShell`, `HomeListRow`,
`HomeChip`, `HomeEmptyPanel`, `HomeInsightCarousel`, `homeHorizontalSwipe`, `HomeSectionHeader`.

> **Correction.** An earlier draft of this plan said "every symbol is `internal`, so nothing outside
> `ui/screens/home/` can use them." That is wrong, and it matters. Kotlin's `internal` is scoped to
> the **module**, not the package — `internal val DashboardWidgetRows` lives in `ui.components` and
> is used by `ui.screens.settings`; `internal fun examStatusText` lives in `ui.components` and is
> used by `ui.screens.academics`. There are 54 such cross-package `internal` usages in `commonMain`.
> The only thing `internal` excludes is the *other* Gradle module (`:androidApp`), which does not
> need these. So the primitives were app-wide already, and "make them `public`" was never the fix.

What was actually wrong was **placement plus a dependency inversion**: a design system sitting
inside `ui/screens/home/`, and `HomeInsightCarousel` taking an input type (`HomeInsightSlide`) that
was declared in `HomeModels.kt` — so the design layer depended on a screen's model, not the other
way round.

Both are fixed:

* `HomePrimitives.kt` + `HomeTokens.kt` → `ui/design/` (`git mv`, history preserved).
* `HomeInsightSlide` moved out of `HomeModels.kt` into `HomePrimitives.kt`, next to the carousel
  that consumes it. It is the component's input contract — every field is one the component
  renders — so it belongs there; `HomeModels` builds the list and `SimplifiedHomeScreen` passes it.
* `internal` **kept**. Narrower visibility is correct when no other module needs the symbols.
* Net change: 2 file moves, 1 type move, **4 wildcard imports** (`ui.design.*` into `HomeModels`,
  `SimplifiedHomeScreen`, `HomeTimetableSheet`, `HomeWeekStrip`). Nothing else referenced them.

Do **not** port `shared/index.ts`'s ~22 re-exports from `@amazecontinuityprojects/amazeui` — that is
the older gray/blue-band dialect and Node's own primitives supersede it.

### What is missing

| Node | Kotlin | action |
|---|---|---|
| `ListSkeleton` | **nothing** — no Skeleton/Shimmer anywhere | build; loading is currently `isLoading` + `SyncProgressPopup` only |
| 9 tone maps (`red amber emerald blue indigo sky violet cyan zinc`) | `HomeTone` has **5**: `SUCCESS WARNING DANGER ACCENT NEUTRAL` | **decision needed** — see below |
| `PageShell` | `ScreenHeader` + `HeaderSpacer` (different mechanism: a single floating header owned by `App.kt`) | keep Kotlin's; it is arguably better |
| `Switch`, `SettingRow`, `ToggleRow`, `SelectField` | `SettingsComponents.kt` (`SettingsRow`, `SettingsSwitchRow`, …) | equivalent, adopt |
| `SegmentedControl` / `ChipTabs` | `AmazeSegmentedControl` (`Components.kt:449`) + `AmazePill` | equivalent; `AmazeSegmentedControl` takes `List<Pair<T,String>>` and loses `ChipTabs`' icon+scroll variants — extend it |

### The tone decision — needs a call

Node has 9 tones. `HomeTokens.kt:374-377` cites `docs/social-tt/11-ui-redesign.md` as **explicitly
capping the app at three semantic hues plus the accent**. A faithful 9-tone port therefore
contradicts a documented design decision already taken in this repo.

Recommendation: **keep 5 tones.** `blue`/`sky`/`violet`/`indigo`/`cyan` are accent-family choices
that `AccentTheme` already exposes (`OCEAN/FOREST/VERDANT/LAVENDER/SUNSET/CUSTOM`). Porting the
other four as tones would give two competing ways to say "blue". The cost is that any Node screen
using `indigo`/`cyan` as a *semantic* tone needs a deliberate mapping to `ACCENT` or `NEUTRAL` —
recorded per screen during its port, not guessed.

---

## 5. The duplication collapse

Node has the same fact derived in several places, with the results disagreeing. This is the main
reason the interpretation layer is worth building rather than a mechanical port.

| fact | implementations | conflict |
|---|---:|---|
| overall attendance % | 5 — `Main.tsx:317`, `MobileHome.tsx:315`, `AttendanceTabs.tsx:236`, `AttendanceSummary.tsx:46`, `CalendarSubpage.tsx:366` | four different threshold rules; `MobileHome` hardcodes 80/75 and ignores `settings.targetAttendance` |
| OD hours | 4 — `Main.tsx:319`, `MobileHome.tsx:293`, `StatCards.tsx:18`, `NavigationTabs.tsx:295` | two re-walk `viewLink` instead of reading the atom handed to them |
| timetable day→card map | 3 — `lib/attendanceTimetable.buildAttendanceDayCardsMap`, `CourseDetailSubpage.tsx:411`, `timetable/buildBands.ts` | `:411` is a strict subset: no merge, no Saturday override, its own time parser |
| course grouping (theory/lab merge) | 2 — `SimplifiedAcademicsPage.tsx:247`, `CourseDetailSubpage.tsx:153` | the same algorithm written twice, ~140 lines each |
| time parser (`"8:30"` → minutes) | 3 — `lib/social/schedule.ts:toMinutes`, `CourseDetailSubpage.tsx:443`, `AttendanceTabs.tsx:74` | `attendanceTimetable.ts:10-25` says the first two "agreed on the real vocabulary by coincidence, not by design" |
| grand weightage (credit-weighted embedded blend) | 1 canonical + ad-hoc re-implementations | `calculateGrandWeightage` (`SimplifiedAcademicsPage.tsx:111-203`) is the formula of record |

**Target: one Kotlin function per row, in `Projections`, each with a test.**

Already correct in Node and to be ported as-is, not re-derived:
`lib/attendanceSummary.ts`, `lib/attendanceTimetable.ts`, `lib/calendarDay.ts`,
`lib/gradeHistory.ts`, `lib/marksPredictor.ts`, `lib/examSchedule.ts`,
`lib/social/schedule.ts`, `lib/slots.ts`, `lib/freeClassrooms.ts`, `lib/timetableMetrics.ts`,
`lib/libraries/koha.ts`, `lib/payments.ts`, `lib/taskMatch.ts`, `lib/curriculum.ts`.

### Three known inconsistencies to normalise, not propagate

* **"Is this a lab?" has three answers in Node.** `CourseDetailSubpage.tsx:695` tests
  `courseCode.endsWith("(L)") || endsWith("(P)")`; `SimplifiedMobileHome.tsx:776` tests
  `slotName.startsWith("L")`; the grouping code at `CourseDetailSubpage.tsx:173/204/245/266`
  tests `courseType.includes("lab") || slot.startsWith("l") || slotName.startsWith("l")`. The
  same student, the same course, three different lab verdicts — and lab decides the OD-hour
  weight *and* the bunk divisor. **Kotlin already has the one answer:**
  `AcademicDerivers.isLabCourse(code, type, slots)`. Every caller passes `isLab` derived from
  it; nothing re-derives the rule. This is why `Projections.bunkableClasses` takes `isLab` as a
  parameter instead of reading a course shape itself.
* **Moodle due field.** `lib/calendarDay.ts:1318` reads `m.due`;
  `SimplifiedMobileHome.tsx:385` reads `t.dueDate`. Kotlin's corrected `LMSAssignment` has `due`
  plus `day/month/year` — the interpretation layer should expose one
  `deadline: Instant?` and both consumers read it.
* **`config.json` consumed three ways** — raw import (`lib/social/schedule.ts:49`), typed casts in
  `attendanceTimetable.ts:72`. Also `lib/slots.ts` uses 5 days (`mon`..`fri`) while `config.slotMap`
  has 7, and FFCS days are lower-case while `slotMap` keys are upper-case. **Node's problem, not
  Kotlin's.** Checked 02 Oct 2026: no Kotlin file reads `config.json` or `chennai.json` at all —
  there is no `ConfigLoader` to write, and the earlier claim that `DailyPlanner.kt:3` imports it
  was wrong (line 3 is `androidx.compose.foundation.background`). Kotlin carries a hardcoded copy,
  `config/SlotMap.kt`, consumed by ~10 files. A `SlotCalendar` value object is therefore only
  worth building if the copy needs to become data-driven.

  **The copy had already drifted, and that is the real finding.** Diffing all 164 entries against
  `config.json`: `WED/L18` and `THU/L24` read `12:35-1:25` where every other day's sixth-period
  lab — and `config.json` — says `12:30-1:20`. They had been copied from the *theory* sixth period
  instead of the lab one, so those two labs rendered five minutes late while five days were right.
  Fixed in `SlotMap.kt`, and `SlotMapTest` now pins all seven sixth-period labs plus range
  well-formedness, so the copy cannot drift silently again.

---

## 6. Sequencing

Each wave ends at a gate: green suite, no screen reading a `*Res`, no duplicated formula.

### Wave 0 — foundations *(blocks everything)*

**Complete ✅** — all five items closed, plus the two things §8 flagged for this wave (`HomeModelsTest`
and the `SlotMap`/`config.json` correction). Gate: `:shared:jvmTest` 322/322 green, release APK
builds clean.

| # | item | status |
|---|---|---|
| 1 | Move `HomeTokens`/`HomePrimitives` out of `ui/screens/home/` | **done ✅** — see §4; `internal` kept, 4 wildcard imports |
| 2 | `AppState.domain` (Step 0 above) | **done ✅** — `combine(AppDataStore.data, selectedSemester)` → `stateIn(Eagerly)` |
| 3 | `SlotCalendar` value object + `ConfigLoader` | **resolved differently ✅** — Kotlin never reads `config.json`; see below |
| 4 | Canonical formulae in `Projections`, one per duplication row | **done ✅** — attendance %, status band, credits, OD, time parsers, bunk margin. See the notes below for what turned out not to be a duplicate. |
| 5 | `Actions` skeleton: `sync`, `refreshCurriculum`, `completeTask`, `clearCache` | **done ✅** — `domain/Actions.kt` + 14 `ActionsTest`s; see the notes below |

Item 5 shipped the **contract** and the commands that can be correct today:

* `SyncOutcome` is the plan's shape verbatim (`Synced` / `Failed` / `NeedsCaptcha` / `Skipped`),
  and `SyncScope(semesterId, modules, force)` carries intent as a value with a stable `label()` for
  log lines and `Failed.module`.
* `completeTask` / `removeTask` / `clearCache` are fully implemented and tested: idempotent
  completion answers `Skipped` rather than writing twice, a missing id answers `Failed` rather than
  succeeding silently, and `clearCache` drops the in-memory snapshot *and* the persisted
  `CACHE_APP_DATA` key — deliberately not `SettingsManager.clearAll()`, because forgetting what you
  fetched and forgetting your credentials are different actions.
* `sync` / `refreshCurriculum` are the **seam**, and they say so honestly. `AppState` owns the ~65
  module fetches and `launchSweep` is fire-and-forget, so nothing registers a runner yet and
  `sync` answers `Skipped`. The day the sweep is joinable — `sweepJob` already exists and is
  awaitable — `AppState` calls `Actions.registerRunner` once and every caller (screen, widget,
  notification) gets the same real result without any of them touching `AmazeClient`. `registerRunner`
  takes `null` to uninstall, which is what lets a test swap in a fake.

One thing this turned up that matters beyond the new file: **`AppDataStore.tasks` is a derived
`stateIn` flow, so it can still be showing the previous snapshot a dispatcher hop after a write.**
The first test run failed three assertions for exactly that reason — `completeTask` read a stale
list and reported `no such task` for a task that had just been added. A command that checks before
it writes must read the source of truth, so `Actions` uses `AppDataStore.data.value.tasks`
(`_data.asStateFlow()`, synchronous). Recorded under structural debt, because any other
read-modify-write against a derived flow has the same bug.

Item 4, measured against **Kotlin** rather than Node (the counts differ):

| fact | Kotlin implementations before | after |
|---|---:|---|
| headline attendance % | 6 — `HomeModels.summariseHomeAttendance`, `DashboardWidgets` ×3, `WidgetDataUtils.getAttendanceStats`, `AcademicsScreen` | `Projections.summariseAttendance` |
| attendance status band | 2 — `homeAttendanceStatus`, `DashboardWidgets`'s inline `attColor` | `Projections.attendanceStatus` |
| OD hours | 2 with **different lab detection** — `AcademicDerivers`, `WidgetDataUtils` | shared `Projections.isOdStatus` + `odHours` + `AcademicDerivers.isLabCourse` |
| lab detection | 3 — `Projections.isLabType`, `AcademicDerivers.isLabCourse`, plus an inline copy inside `computeODHours` | `AcademicDerivers.isLabCourse(code, type, slots)` |
| credits earned | 2 byte-identical copies | `Projections.creditsEarned` |
| bare time → minutes | 3 heuristics + 3 ad-hoc copies | `TimeMath.toMinutes` (VIT slot), `toMilitaryMinutes` (24-hour), `toClockMinutes` (meridian) |
| `"a" - "b"` range split | ~10 copies of `split("-")[0]` / `[1]` | `TimeMath.toRange` |
| "now" in minutes | 2 — `AttendanceTimetable.currentTimeInMinutes`, `HomeModels.homeNowMinutes` | `TimeMath.nowMinutes` |
| bunkable margin | 2 — `BunkOMeter`'s inline `floor(...)`, plus its inline copy of the status band | `Projections.bunkableClasses` + `Projections.attendanceStatus` |
| classes-to-target (the deficit half) | **0 in Kotlin** — the card has no "Need N" branch at all | `Projections.classesToTarget` |

**Time parsers were not collapsed into one function, deliberately.** The three Kotlin copies
disagreed because they were handed three different formats, and merging them would have picked one
meaning and corrupted the other two: `TimeMath.toMinutes("07:00")` is 19:00 (VIT writes a bare
afternoon slot with no meridian anywhere), `toMilitaryMinutes("07:00")` is 07:00 (`TaskModels.startTime`
is documented `HH:mm`), and `toClockMinutes("07:00")` is null (no meridian to honour). Each format now
has a named entry point with its own KDoc, and `TimeMathTest` pins the three-way divergence as the
reason they must stay separate. `FreeClassrooms`, `DailyPlanner` and `ExamUtils` delegate rather than
re-implement; `AttendanceTimetable.parseAttendanceTime` and `AcademicDerivers.slotStartMinutes` go
through `toMinutes`/`toRange`.

This also fixed a latent bug: `toMinutes` never trimmed, so a range end arriving as `" 9:50"`
parsed the hour `" 9"` to null → 0 and returned **minute 50 instead of 590**. Every caller that
split a `"9:00 - 9:50"` slot on `-` without trimming was affected; VIT's starts happen to always be
`:00`, which is why it never showed.

`AcademicsScreen` was the Kotlin equivalent of Node's known bug: it computed `avgAttendance` as the
**mean of the per-course percentages**, so a course with no held classes dragged the headline down
and a one-class course counted as much as a forty-class one. Now it sums attended/total like every
other surface. `WidgetDataUtils.computeODHours` and `AcademicDerivers.computeODHours` could also
disagree — the former only tested `slotName.startsWith("L")`, the latter also read the `(L)` code
suffix, so a lab with a non-lab slot was 1 hour in a widget and 2 on the home screen. Both now
route through one lab test.

Still to do under item 4: **nothing — `grandWeightage` turns out to be a false lead for Kotlin.**

`calculateGrandWeightage` (`SimplifiedAcademicsPage.tsx:111`) computes the credit-weighted
embedded blend *at render time*, because Node has no translation layer to do it earlier. Kotlin
already has the same blend one layer down, in `AcademicMerge.mergeEmbeddedPair`
(`AcademicMerge.kt:46`): `(tTotal * tCredits + lTotal * lCredits) / totalCredits`. Doing it in the
translation layer is the better placement, so this is not a gap to fill.

It is **not byte-identical**, and that difference should be resolved when the marks screen is
ported rather than papered over now:

| | Node `calculateGrandWeightage` | Kotlin `mergeEmbeddedPair` |
|---|---|---|
| blends | `weightagePercent` / `weightageMark` | `maxMark` / `totalMark` |
| unknown-credit fallback | theory 3 : lab 1 | plain 1 : 1 mean |

What genuinely remains duplicated is four DTO-level display copies in `CourseDetailScreen.kt`
— `totalWeighted` / `totalWeightPct` at lines **404-405, 992-993, 1154-1155, 1504-1505**, plus two
different `projectedPct` formulas (line 413 prefers a stored `maxMark`, line 994 does not consult
one). They read `AssessmentItem`, a transport DTO. Moving them into `Projections` now would mean
either putting a DTO type into a domain file or writing an adapter for no screen that has migrated
yet — so they collapse for free in **Wave 1**, when `CourseDetailScreen` moves onto
`Projections.marks` / `AssessmentRow`. Forcing it in Wave 0 would be the wrong layer.

`bunkableMargin` is now canonical and tested, but two fidelity gaps were found and deliberately
**not** fixed here, because both change what a card displays and belong with that card's port:

* **The lab divisor.** `Projections.bunkableClasses`/`classesToTarget` take `isLab` and halve,
  but `BunkOMeter` passes nothing, so the card still counts hours not sessions as Node does.
* **The deficit branch.** `CourseDetailSubpage:780` and `SimplifiedMobileHome:786` both show
  "Need N more classes to reach 75%" when below target. `BunkOMeter` has no such branch — it only
  counts critical/warning courses. `classesToTarget` now exists so it can.

**Gate:** every fact in the table above has exactly one Kotlin counterpart and a test that fails if
it regresses. Attendance, OD, credits, lab detection, credits-earned, the time parsers and the
margin arithmetic all meet that. Item 4 is **closed**; the four DTO-level weightage copies are
deferred to Wave 1 for the layering reason above, and `examState` to open decision 7.

**Suite: 224 → 236 → 247 → 255**, all passing (`:shared:jvmTest`).

### Wave 1 — the academic spine *(biggest fidelity win)*

`AttendanceScreen`, `CourseAttendanceScreen`, `CourseDashboard`, `CourseDetailScreen`,
`ExamScheduleScreen`, `GradesScreen`.

`CourseDashboard` first, because `CourseDetailScreen` and the course-grouping collapse both depend
on its grouping function being right.

Port `lib/gradeHistory.ts` and `lib/examSchedule.ts` behaviour verbatim, including
`classifyExamState`'s `past|today|upcoming`.

**Gate:** no screen in `ui/screens/academics/` names `StoredCourse`, `AttendanceItem`,
`MarksCourseItem`, `GradeItem` or `ExamItem`, and no `toXItem()` shim call remains. (Today all five
are converted *into* DTOs by `toAttendanceItem()` / `toMarksCourseItem()` / `toGradeItem()` shims —
those get deleted, not ported.)

> **Read of the gate, recorded while executing.** The literal wording says *any* screen in that
> directory, but 9 of its 14 files are assigned to Waves 2, 3 and 5 — so read literally the Wave-1
> gate cannot be met without absorbing those waves. Taken as written (the five type *names* plus the
> shim calls) it is directory-wide and achievable in Wave 1. It does **not** mean "stops inferring
> `StoredCourse` through `sem.courses.values`"; that is DoD 1, which applies to the six screens this
> wave ports. On that reading `GPAPredictorScreen` still reads `AppState.academic` for GPA/credits

**Complete.** Gate re-run 03 Oct 2026: **0 hits** for `StoredCourse`, `AttendanceItem`,
`MarksCourseItem`, `GradeItem`, `ExamItem` or any `toXItem()` call across all 14 files in
`ui/screens/academics/`, and **390 tests green** in `:shared:jvmTest` (24 suites, 0 failures).

New files this wave: `domain/ExamSchedule.kt` (the `examSchedule.ts` port) and
`domain/GradeHistory.kt` (the `gradeHistory.ts` port), each with a test file ported from the
Node suite. `Projections` gained `semesterAttendance` / `currentSemesterAttendance` /
`semesterExams` / `selectedSemesterExams` / `examsForKnownSemester` / `semesterIdsWithExams`.

Decisions 7 and 8 were executed rather than left open - see 9.

Divergences from Node, written down as DoD 4 allows:

* **`Marks % Trend` now uses `GradeHistory.SemesterRow.avgScore`**, which averages only courses
  carrying a numeric `grandTotal`. The inline version it replaces summed every course with a
  blank total as `0` and divided by the course count, so one unpublished course pulled the whole
  trend down. Node tests `avgScore`; the inline version had no test.
* **`Grade Spread` is now ordered by `GRADE_ORDER`** (S -> F) instead of by course order, which
  is the order Node's `gradeDistribution` returns.
* **`Highest` / `Lowest` / `Avg Score` now use Node's "parseable number" rule** rather than the
  screen's own `> 0` filter. The only observable difference is a course whose `grandTotal` is
  literally `"0"`.
* **Semester switcher chips now read `GradeHistory.semesterName`.** The inline rule was
  `if (id.endsWith("1")) "FS yy" else "WS yy"`, which is exactly the bug `gradeHistory.ts`'s KDoc
  calls out: it labelled summer terms "Winter". This was the one place in the app still
  re-deriving the label; `CourseDetailScreen` already had the correct formatter.
* **`embeddedSegments` and `segmentBlend` are not ported.** They read Node's per-semester *marks*
  cache, where an embedded course is still two rows under one code. This app merges that pair at
  ingest (`AcademicMerge.mergeEmbeddedPair`), so the halves no longer exist; a blend computed from
  the merged row would be a number nothing produced. `CourseGroup` still surfaces `theory`/`lab`
  for legacy snapshots only, and `segmentBlend` needs both halves, so porting it would return
  `null` and change nothing - which is the honest outcome.
* **`toneForGrade` / `GRADE_TONE` are not ported.** Node's six semantic tone names do not exist in
  this theme; grade chips go through `gradeColorIndex` onto `AmazeColors.chart1..5`. Porting the
  names would produce a mapping nothing reads.
* **`cumulativeGpa` is ported and tested but not called.** The CGPA summary it needs
  (`cgpa` / `creditsEarned` / `creditsRequired`) lives on `MarksRes`, which is not part of
  `DomainSnapshot`, and `GradesScreen` does not show a cumulative figure.

The three `toXItem()` shims in `AcademicDerivers.kt` stay for now: nine call sites outside this
directory still use them (`DashboardWidgets` x6, `SocialScreen` x2, `SimplifiedHomeScreen` x1).
They are Wave 2 and Wave 5 work, and the gate note above scopes this wave to the academics
directory.


### Wave 2 — calendar & home, together

`CalendarScreen` and `SimplifiedHomeScreen` share `lib/calendarDay.ts` (69 KB, the largest test file
in Node). Port `buildEnrichedCalendars` once; do not port it twice.

This is also where Node's strongest UI test lives —
`simplifiedMobileHome.weekStrip.test.tsx` (13.9 KB: 7 discs, Monday-first, exam/holiday tints,
today ring, swipe paging) — and `HomeWeekStrip` already implements all of it.

> **Premise corrected while executing, 06 Oct 2026.** The sentence above says the two screens
> "share `lib/calendarDay.ts`". They do not. Node keeps **two** calendar interpretations:
> `SimplifiedMobileHome.tsx:601-697` has its own week scan, its own `extractDayOrderOverride`
> (`:100`), its own ten-word `HOLIDAY_WORDS` (`:648-658`) and its own blank-text rule (`:633-635`),
> while `calendarDay.ts` is the *calendar page's* engine. Kotlin's `buildHomeWeekDays` is a faithful
> port of the **first** of those, not a duplicate of the second — so rebinding the home onto
> `buildEnrichedCalendars` would have **introduced** a divergence instead of removing one, which
> DoD 4 forbids. Held: `buildEnrichedCalendars` is ported once, for `CalendarScreen` only, which is
> what the "port it once" line above actually asks for. All ten `HOLIDAY_WORDS` match Node exactly.

> **Read of the gate.** Wave 2 has no gate line of its own, so §10 DoD 1 + the note at the end of
> Wave 1 are what apply: of the nine `toXItem()` call sites named there, exactly one is in this
> wave's directories (`SimplifiedHomeScreen:176`), so that is the one removed. The other eight
> (`DashboardWidgets` x6, `SocialScreen` x2) stay Wave 5.

**Complete.** 06 Oct 2026. Sweep over `ui/screens/academics/` (16 files) and `ui/screens/home/`
(4 files): **0 `toXItem()` calls**. Four `ExamItem` names remain in that sweep
(`HomeModels.kt:421,636,796`, `SimplifiedHomeScreen.kt:1036`) — these are the *persisted*
`SemesterData.exams: List<ExamItem>` (`ScheduleModels.kt:6`), a storage type rather than the
DTO-into-which-a-projection-is-converted meaning Wave 1's gate was written against, so they are
recorded here instead of being swept away. Suite: **30 suites, 519 tests, 0 failures**;
`:androidApp:assembleRelease` green, APK 4,604,028 bytes.

`SimplifiedHomeScreen`'s attendance summary and critical count now come from
`Projections.semesterAttendance(domain, sem.semesterId)` over the **resolved** semester — `sem` is
`selectedSemester` only when that semester has courses, otherwise `resolveCurrentSemester`, so
keying the projection on `selectedSemester` would have read the wrong term.

New files this wave: `domain/ExamSeries.kt`, `domain/CalendarClassifier.kt`,
`domain/CalendarDay.kt` (~1015 lines, the `calendarDay.ts` port), `domain/CalendarAttendance.kt`,
`domain/CalendarEnrich.kt`, `domain/CalendarProjection.kt`; tests `CalendarClassifierTest` (12),
`CalendarDayTest` (21), `CalendarAttendanceTest` (26), `CalendarEnrichTest` (52),
`CalendarProjectionTest` (9); `HomeModelsTest` grew 49 → **54** (the week-strip ports plus the
blank-text rule below); and — decision 5 executed — `jvmTest/.../home/HomeWeekStripUiTest.kt` with
the first four Compose UI tests in the project. Infrastructure: `compose-ui-test` /
`compose-ui-test-junit4` in `gradle/libs.versions.toml` and a `jvmTest.dependencies` block in
`shared/build.gradle.kts`. Compose's `runComposeUiTest` is imported from
`androidx.compose.ui.test.v2` (the un-namespaced one is deprecated), and every test wraps its
content in `AmazeTheme {}` because `LocalAmazeColors` throws without a provider.

DoD 1 is **not** met by either screen this wave, recorded rather than claimed:

* `CalendarScreen` still collects `AppState.moodleData` (`MoodleRes?`) and
  `AppState.registeredEvents` (`EventHubRegisteredEventsRes?`) alongside `domain`.
* `SimplifiedHomeScreen` still collects `AppState.academic` (`AcademicData`), `calendar`,
  `calendarsList` and `moodleData`, and still reads `sem.courses` for the empty-term guard,
  `sem.exams`, `sem.gpa`, `buildHomeTimetable(sem)` and `creditsOf(sem)`.
* DoD 3 is met by **no** screen in the project yet: `Actions` exists
  (`domain/Actions.kt`, with `sync` / `refreshCurriculum` / `completeTask` / `removeTask` /
  `clearCache`) but zero screens call it; they call `AppState.refresh*` / `AppState.loadAllData()`.

Divergences from Node, written down as DoD 4 allows:

* **The home's calendar interpretation stays separate from `buildEnrichedCalendars`**, per the
  premise correction above.
* **`eventText` lost its third fallback.** Kotlin had `text.ifBlank { category }.ifBlank { type }`
  where Node reads `ev.text || ev.category || ""` and skips an empty result. A row carrying a
  `type` of `"Holiday"` but no words therefore used to mark a holiday here and marks nothing on the
  web. One line, pinned by `HomeModelsTest.an event with no text and no category is skipped and
  its type does not speak for it`.
* **The OD tracker is not wired into the calendar.** `CalendarScreen:359` passes
  `odTracker = emptyMap()` — `SettingsManager.getODTrackerState()` is raw JSON the tracker screen
  owns, so hand-recorded OD days do not reach `buildEnrichedCalendars`. `CalendarSources.odTracker`
  exists and is tested; only the feed is missing.
* **No exam series key.** Node's schedule is `Record<string, ExamItem[]>`; Kotlin persists a flat
  `List<Exam>`, so every caller passes `mapOf("" to exams)`. `ExamSeries.sameSeries("", …)` is
  therefore false and milestone folding stays off.
* **The EventHub confirmation filter is ported but inert.** `CalendarEnrich.isRegistrationConfirmed`
  (`:267`) and its call at `:284` mirror Node's function in `calendarDay.ts:1473`, but
  `toCalendarRegistrations` (`CalendarProjection.kt:87`) has no `paymentStatus` to pass — the field
  does not exist on `EventHubRegisteredEvent` — so `paymentStatus` is always null, the function
  takes its `status.isEmpty()` branch and returns `true`. Paid-but-unconfirmed registrations
  therefore still show on the calendar; they would not on the web.
* **No `totalDays` on payload months** — `MonthModel.summary.total` still computes it.
* **No profile photo on calendar events.** `CalendarSources.profileImageUrl` defaults to `null` and
  `CalendarScreen` never passes it, so every EventHub row renders bare. Node gates the photo with
  `shouldShowProfilePhoto(settings)` in `CalendarSubpage` before the model is built; Kotlin has no
  such function — the only mention of it anywhere is the KDoc on that field
  (`CalendarEnrich.kt:76`) — and no UI file names `profileImageUrl` at all.
* **`utils/AnalyzeCalendar.kt` is not `analyzeCalendar.ts`** — different contract, still used by
  `AmazeClient.kt:452` on the live sync path. Out of scope for this wave.
* **OD is derived from attendance, not read from a payload.** `odRecordsFrom` only emits for status
  `on duty|od|onduty` and weights a lab-typed slot 2 against a theory slot's 1.
* Compose CSS-tint assertions (`bg-*` classes) from `weekStrip.test.tsx` are not ported — there are
  no CSS classes to assert on, so the equivalent checks are made against the disc's
  `contentDescription`.

Still to port: the three remaining §8.3 Compose UI tests (`InsightCarousel` swipe,
`HorizontalTimetableGrid` rowSpan geometry, `examScheduleDisplay.filter`), Node's structural lint
`page-shell-children.test.ts`, and `calendarDay.test.ts`'s `dayOrderNote` cases (`:211-242`).
`dayOrderNote` is ported (`CalendarDay.kt:577`) but currently has **no caller anywhere in
`commonMain` and no test** — it is the note that stays silent when a day's announced order already
matches its real weekday, so whoever wires `CalendarScreen` to it needs both.

### Wave 3 — curriculum & prediction

`CurriculumScreen`, `GPAPredictorScreen`, `TimetableComponents`.
Port `lib/curriculum.ts` and `lib/marksPredictor.ts`. Kotlin's `CurriculumScreen` already reads
`CurriculumRes.categories/details/totalCredits` directly; move that reading into a projection.

### Wave 4 — campus

`HostelScreen` (mess/laundry/leave/counselling), `PaymentsScreen`, `LibrariesScreen`,
`TransportScreen`, `CabShareScreen`, `FfcsPlannerScreen`, `FreeClassroomsScreen`.

Port `lib/libraries/koha.ts` and `lib/payments.ts`. Mess and laundry are **CDN JSON, not the API**
(`public/data/mess/VITC-*.json`) — confirm whether Kotlin should keep fetching those or fold them
into a sync module.

### Wave 5 — engagement & services

`EventHubScreen`, `ClubDetailScreen`, `ClubHubScreen`, `CircularsScreen`, `TasksScreen`,
`MoodleScreen`, `SocialScreen`.

`TasksScreen` is the largest file in the project (2417 lines) and Node's `Task` type carries
`WeekChunk[]`, `reminders[]` and a `pomodoro` block. `HomeworkTask` in Kotlin has none of those —
**decide whether the Kotlin model or the Node model wins before porting.** Node's
`lib/taskMatch.ts` (LMS→task matching) has no Kotlin counterpart at all.

### Wave 6 — settings, profile, shell

`SettingsScreen`/`SettingsHub`, `ProfileHub` + profile pages, `MoreScreen`, `CommandPalette`,
`BottomNavigationBar`, `HeaderConfigs`.

---

## 7. Structural debt to fix during this, not after

**Navigation is duplicated four ways.** Adding a route means editing `App.kt:166-213` (48 `when`
arms), `MainTabPager.kt:77-99` (17 arms), `HeaderConfigs.kt:20-267`, and
`BottomNavigationBar.kt:95`. Collapse to one registry — a `Screen` → `(composable, icon, label,
header)` table — before Wave 6 adds more routes.

**`Screen` is a flat enum with 43 entries and no argument slots**, so every "route argument" is a
separate `StateFlow` on `AppState` (11 deep-link target flows: `settingsSectionTarget`,
`transportRouteTarget`, `curriculumCourseTarget`, …). That is workable but it is why
`CourseDetailScreen` reads `selectedCourseCode` + `selectedCourseSemester` as globals. A
`sealed interface Screen` with data classes for the argument-carrying routes would fix it; defer,
since it touches every screen.

**`DashboardWidgets.kt` is 2,352 lines with every widget body `private`.** Widget reuse across
screens is impossible without editing the file. Extract each widget to its own file as it is
touched.

**Read-modify-write against a derived flow reads a stale snapshot.** Every `AppDataStore` module
flow (`tasks`, `academic`, `curriculum`, …) is `_data.map { … }.distinctUntilChanged()
.stateIn(scope, Eagerly, …)` — the value is refreshed by a collector on `Dispatchers.Default`, not
at the moment `_data` changes. So `AppDataStore.tasks.value` can still hold the previous list a hop
after `addTask` returned, and any code that checks a list *before* mutating it is racy.
Found the hard way writing `ActionsTest`: `completeTask` answered `Failed("no such task")` for a
task added two lines earlier. `Actions` reads `AppDataStore.data.value.tasks`
(`_data.asStateFlow()`, synchronous) instead. Grep for `.value` on a derived flow before trusting it
in a command; a screen collecting as a `StateFlow` is fine, because it only ever reads the latest.

---

## 8. Testing strategy

**There are currently zero Compose UI tests in any source set** — no `createComposeRule`, no
`runComposeUiTest`, no `compose.uiTest` dependency (`shared/build.gradle.kts:93-95` declares only
`kotlin.test`). Every one of the 322 tests is pure logic.

That is not a gap to apologise for; it is the reason the project has a `jvm()` target at all. The
established pattern — `HomeModels.kt` is 24 pure functions written deliberately so they could be
tested, and `SimplifiedHomeScreen` is thin — is the right one. Policy:

1. **Derivations go in `Interpret` (or `*Models.kt`), never in a composable.** Test them there.
2. Every collapsed formula (§5) gets a test before it gets used by two callers.
3. For UI that *is* worth pinning, port Node's tests to Compose UI tests: the week strip,
   `InsightCarousel` swipe, `HorizontalTimetableGrid` rowSpan geometry, and
   `examScheduleDisplay.filter` are the four with real regression value.
4. **Port Node's structural lint.** `src/__tests__/page-shell-children.test.ts` asserts no converted
   `PageShell` collapsed to a single element child (`:140-149`) — the cheapest guard against a
   header regression in a port, and it is a pure test with no UI harness needed.

Note the untested risk this creates: `HomeModels.kt`'s 24 derivations — the most transplant-critical
code in the repo — still have **no test file**. That should be fixed in Wave 0.

**Closed.** `commonTest/.../ui/screens/home/HomeModelsTest.kt` now carries **49 tests** over those
derivations: the bunk arithmetic (including the deliberate refusal to halve for a lab), the four
status bands at their exact boundaries, live-class progress at both ends of a slot, flavour
precedence, `extractDayOrderOverride`'s two-condition guard, `buildHomeWeekDays`' holiday /
instructional / reorder handling and its year match, timetable merging versus non-adjacent
sessions, and Moodle deadline filtering. Writing it also corrected this file's own premise: the
KDoc in `HomeModels.kt` claimed `DayOfWeek` is Sunday-first and therefore at odds with
`AttendanceDay` — it is Monday-first (ISO), as `DailyPlanner`, `AttendanceScreen` and
`buildHomeWeekDays` itself all assume via `dayOfWeek.ordinal`. Both enums are Monday-first; the
mapping is still stated once, by name, because nothing ties them together.

Suite: **322 tests, 0 failures** across 20 classes on `:shared:jvmTest`; release APK rebuilds clean.

---

## 9. Open decisions

| # | question | recommendation |
|---|---|---|
| 1 | 5 tones or 9? | Keep 5. `docs/social-tt/11-ui-redesign.md` already caps the app at 3 hues + accent; the other four are accent-family, already covered by `AccentTheme`. |
| 2 | Rename `Home*` primitives on the move out of `home/`? | No — mechanical churn with no user-visible gain. Do it later if ever. |
| 3 | Does `TasksScreen` adopt Node's `Task` (with `WeekChunk`, `reminders`, `pomodoro`) or keep Kotlin's `HomeworkTask`? | Node's. `HomeworkTask` has no schedule or reminder concept at all, so the tasks screen cannot reach Node fidelity without it. It is a persisted-shape change, so it belongs in a Wave 5 migration. |
| 4 | Mess/laundry: keep CDN JSON or fold into a sync module? | Fold in. CDN JSON means the app silently shows nothing offline, and the sanitiser already exists for `MessMenuRes`/`LaundryRes`. |
| 5 | Do we need Compose UI tests at all? | Yes for the four behaviours in §8.3, added as `compose.uiTest` in Wave 2. Not for general coverage. |
| 6 | Is `Projections` the right home, or should interpretation split per concern (`AcademicsInterpret`, `CampusInterpret`)? | Split when any one exceeds ~400 lines. `Projections` is already the awkward single-file pattern (`HomeModels.kt` is 890 lines) that this plan is trying to move away from. |
| 7 | **Which exam "start" is canonical?** Node's `examStartMinutes` (`examSchedule.ts:74`) reads `examTime` first and falls back to `reportingTime`; Kotlin's `ExamUtils.examStartMinutes` (`ExamUtils.kt:119`) does the opposite. They give different `hoursUntilExam` whenever both fields are present and differ. | Port Node's order, but only with a deliberate test — `ExamUtilsTest.examStartFallsBackFromReportingToExamTime` currently pins Kotlin's order as correct. Reporting time is when you must be seated, exam time is when the paper starts; the answer changes what "next exam in 2h" means, so it is a product call, not a dedup. **Executed 03 Oct 2026**: `ExamUtils.examStartMinutes` now reads `examTime` first, and `ExamUtilsTest.examStartPrefersThePaperTimeOverTheReportingTime` replaces the test that pinned the old order. |
| 8 | **`examState`: port or keep?** Node's `classifyExamState` is a 3-way `past/today/upcoming` that is *time-of-day* aware (a CAT that ended at 12:30 is `past` at 3 PM). Kotlin's `examStatusText` is `PAST`/`TODAY`/`IN 3d`/`""`, hour-based with a 14-day horizon and no end-time awareness. | Not a duplication — one implementation each, with different contracts. Keep Kotlin's for now (it is richer for display), add Node's time-of-day rule *behind* it so a finished same-day paper reads `PAST` rather than `TODAY`. Do this when the exam screen is ported, not in Wave 0. **Executed 03 Oct 2026**, with one correction to the premise: Kotlin's `examStatusText` already returned `PAST` whenever `hoursUntilExam < 0`, so a finished same-day paper was never at risk of reading `TODAY`. What the guard actually changes is the case where **no start instant exists at all** - a dated paper with an unparseable time used to report `""` for ever, and now follows the calendar. `ExamStatusTextTest` pins that, plus the wording it leaves alone. |

---

## 10. Definition of done, per screen

A screen is ported when:

1. It binds **only** to `AppState.domain` + `Projections` — no `*Res`, no `Stored*`, no
   `toXItem()` shim.
2. Every number it shows comes from a projection, and that projection has a test.
3. It triggers work **only** through `Actions.*` and reacts to the returned result.
4. Its behaviour matches Node for the cases Node tests, or the divergence is written down.
5. It introduces no new formula — anything it needs that is not already a projection gets added to
   §5's table first.
