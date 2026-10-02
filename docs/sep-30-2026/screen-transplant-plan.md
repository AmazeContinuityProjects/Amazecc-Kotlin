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

### Two known inconsistencies to normalise, not propagate

* **Moodle due field.** `lib/calendarDay.ts:1318` reads `m.due`;
  `SimplifiedMobileHome.tsx:385` reads `t.dueDate`. Kotlin's corrected `LMSAssignment` has `due`
  plus `day/month/year` — the interpretation layer should expose one
  `deadline: Instant?` and both consumers read it.
* **`config.json` consumed three ways** — raw import (`lib/social/schedule.ts:49`), typed casts in
  `attendanceTimetable.ts:72` and `DailyPlanner.kt:3`. Also `lib/slots.ts` uses 5 days
  (`mon`..`fri`) while `config.slotMap` has 7, and FFCS days are lower-case while `slotMap` keys are
  upper-case. **One Kotlin `SlotCalendar` value object, loaded once**, is the fix; it also removes the
  last reason for `chennai.json` to be read at runtime.

---

## 6. Sequencing

Each wave ends at a gate: green suite, no screen reading a `*Res`, no duplicated formula.

### Wave 0 — foundations *(blocks everything)*

| # | item | status |
|---|---|---|
| 1 | Move `HomeTokens`/`HomePrimitives` out of `ui/screens/home/` | **done ✅** — see §4; `internal` kept, 4 wildcard imports |
| 2 | `AppState.domain` (Step 0 above) | **done ✅** — `combine(AppDataStore.data, selectedSemester)` → `stateIn(Eagerly)` |
| 3 | `SlotCalendar` value object + `ConfigLoader` | pending |
| 4 | Canonical formulae in `Projections`, one per duplication row | **partly done ✅** — attendance %, status band, credits earned, OD status/hours |
| 5 | `Actions` skeleton: `sync`, `refreshCurriculum`, `completeTask`, `clearCache` | pending |

Item 4, measured against **Kotlin** rather than Node (the counts differ):

| fact | Kotlin implementations before | after |
|---|---:|---|
| headline attendance % | 6 — `HomeModels.summariseHomeAttendance`, `DashboardWidgets` ×3, `WidgetDataUtils.getAttendanceStats`, `AcademicsScreen` | `Projections.summariseAttendance` |
| attendance status band | 2 — `homeAttendanceStatus`, `DashboardWidgets`'s inline `attColor` | `Projections.attendanceStatus` |
| OD hours | 2 with **different lab detection** — `AcademicDerivers`, `WidgetDataUtils` | shared `Projections.isOdStatus` + `odHours` + `AcademicDerivers.isLabCourse` |
| lab detection | 3 — `Projections.isLabType`, `AcademicDerivers.isLabCourse`, plus an inline copy inside `computeODHours` | `AcademicDerivers.isLabCourse(code, type, slots)` |
| credits earned | 2 byte-identical copies | `Projections.creditsEarned` |

`AcademicsScreen` was the Kotlin equivalent of Node's known bug: it computed `avgAttendance` as the
**mean of the per-course percentages**, so a course with no held classes dragged the headline down
and a one-class course counted as much as a forty-class one. Now it sums attended/total like every
other surface. `WidgetDataUtils.computeODHours` and `AcademicDerivers.computeODHours` could also
disagree — the former only tested `slotName.startsWith("L")`, the latter also read the `(L)` code
suffix, so a lab with a non-lab slot was 1 hour in a widget and 2 on the home screen. Both now
route through one lab test.

Still to do under item 4: `grandWeightage` (Node's `SimplifiedAcademicsPage.tsx:111-203`), `examState`
(Node's `classifyExamState`), `bunkableMargin`, and the three time parsers.

**Gate:** every fact in the table above has exactly one Kotlin counterpart and a test that fails if
it regresses. Attendance, OD, credits and lab detection now meet that; the rest are open.

**Suite: 224 → 236**, all passing (`:shared:jvmTest`).

### Wave 1 — the academic spine *(biggest fidelity win)*

`AttendanceScreen`, `CourseAttendanceScreen`, `CourseDashboard`, `CourseDetailScreen`,
`ExamScheduleScreen`, `GradesScreen`.

`CourseDashboard` first, because `CourseDetailScreen` and the course-grouping collapse both depend
on its grouping function being right.

Port `lib/gradeHistory.ts` and `lib/examSchedule.ts` behaviour verbatim, including
`classifyExamState`'s `past|today|upcoming`.

**Gate:** no screen in `ui/screens/academics/` reads `StoredCourse`, `AttendanceItem`,
`MarksCourseItem`, `GradeItem` or `ExamItem`. (Today all five are converted *into* DTOs by
`toAttendanceItem()` / `toMarksCourseItem()` / `toGradeItem()` shims — those get deleted, not
ported.)

### Wave 2 — calendar & home, together

`CalendarScreen` and `SimplifiedHomeScreen` share `lib/calendarDay.ts` (69 KB, the largest test file
in Node). Port `buildEnrichedCalendars` once; do not port it twice.

This is also where Node's strongest UI test lives —
`simplifiedMobileHome.weekStrip.test.tsx` (13.9 KB: 7 discs, Monday-first, exam/holiday tints,
today ring, swipe paging) — and `HomeWeekStrip` already implements all of it.

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

---

## 8. Testing strategy

**There are currently zero Compose UI tests in any source set** — no `createComposeRule`, no
`runComposeUiTest`, no `compose.uiTest` dependency (`shared/build.gradle.kts:93-95` declares only
`kotlin.test`). Every one of the 224 tests is pure logic.

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
