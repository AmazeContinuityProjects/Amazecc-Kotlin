# Evaluation 06 — Modularization & the "Subscribable Hooks" Plan

The user asked: *"look at how to clean out dead code, modularise stuff, make everything like subscribable hooks or smtg"*. This file is that plan, grounded in the current code.

---

## 1. The current architecture (and why it's the root cause of the mess)

```
AmazeClient (object)  ──┐
AppState (object, 2716 ln) ──>  ~50 MutableStateFlow  ──>  screens via collectAsState()
SyncEngine (object)   ──┘
```

- **One god-object.** AppState owns navigation, theme, every data domain (attendance, marks, events, cab, tasks, hostel, payments…), sync orchestration, logout, and even UI header state. 2,716 lines, ~50 flows, ~100 functions.
- **Everything is a singleton object** — untestable, no DI, no scoping; state survives logout; can't have two instances (e.g., semester-scoped state).
- **No repository layer** — the 3 repositories went dead because AmazeClient+AppState absorbed their jobs. Screens call `AppState.x` and `AmazeClient.y` directly.
- **No separation of write/read** — `MutableStateFlow` exposed publicly in places (`AppState.kt:2685-2690` header flows); anything can mutate anything.
- **Sync logic entangled with state** — `loadAllData()` is 660 lines of per-module fetch+cache+update inside one function.

## 2. Target: reactive hooks (Compose-idiomatic, flow-based)

The "subscribable hooks" pattern maps directly to what Compose already does with `collectAsState` — the change is **who owns the flow** and **how screens access it**. Concretely:

```kotlin
// 1) A domain module owns its state (NOT AppState)
class AttendanceRepository(scope: CoroutineScope) {
    private val _state = MutableStateFlow(AttendanceState())
    val state: StateFlow<AttendanceState> = _state.asStateFlow()

    suspend fun refresh() { ... }
}

// 2) Screens subscribe through a single hook — the screen never touches the repo
@Composable
fun useAttendance(): AttendanceState {
    val repo = LocalRepositories.current.attendance   // CompositionLocal (KMP-safe DI)
    val state by repo.state.collectAsState()
    return state
}

// 3) A lightweight state reducer per domain keeps single-source-of-truth
@Immutable data class AttendanceState(
    val data: AttendanceRes? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val syncedAt: Instant? = null,
)
```

**This is a naming convention + ownership change, not a framework change.** `collectAsState` already exists; `CompositionLocal` is already in the codebase (`LocalNfcManager`, `LocalNotificationPermissionManager` are precedents — `NfcManager.kt:12`, `NotificationPermissionManager.kt:9`). No new libraries needed.

### Why it fixes the audit findings

| Current problem | Hooks fix |
|---|---|
| Dead code invisible (singletons mask it) | Every symbol reachable only through a repo with 1 consumer is obviously dead |
| God-object coupling | Each screen subscribes to a narrow flow; nothing else touches `AppState` |
| State survives logout (privacy bug C3) | Repos are constructed per-session or cleared via a single `resetAll()` |
| Sync races (SyncEngine) | One `syncAll` coordinator calling `repo.refresh()` per domain; progress = fold over repo states |
| Untestable | Repos are plain classes with `CoroutineScope` injected; tests use `TestScope` + fake `AmazeClient` |
| Hardcoded settings drift | `SettingsRepository` (typed keys, DataStore/multiplatform-settings) is the only writer |

## 3. Proposed module map

```
com.amazecc.app.shared
├── api/            AmazeClient → split:
│   ├── AmazeClient (http core, auth session)   — keep, fix timeout/expectSuccess
│   ├── AuthApi     login/logout/session        — extracted
│   ├── AcademicsApi attendance, marks, grades, exams, curriculum, calendar
│   ├── CampusApi   circulars, qbank, faculty, syllabus, qcm
│   ├── LifeApi     events, clubs, hostel, transport, cabshare, library, moodle, payments
│   └── Dto/        move request/response DTOs here (strip dead fields)
├── data/           Repositories (one per domain) — the hooks' backend
│   ├── AttendanceRepository     ├── CalendarRepository
│   ├── MarksRepository          ├── TasksRepository
│   ├── GradesRepository         ├── EventsRepository
│   ├── ExamRepository           ├── ClubRepository
│   ├── CurriculumRepository     ├── CabShareRepository
│   ├── HostelRepository         ├── LibraryRepository
│   ├── TransportRepository      ├── MoodleRepository
│   ├── PaymentsRepository       ├── FacultyRepository
│   ├── QBankRepository          ├── SocialRepository
│   └── SettingsRepository       (typed keys, single writer)
├── state/          (shrinks to) AppState = navigation + theme + DI wiring only
├── ui/
│   ├── screens/    unchanged locations, but screens use hooks only
│   ├── components/ keep shared components; delete dead ones (eval/05)
│   └── hooks/      useXxx() thin wrappers (or keep them in ui/screens/)
├── platform/       expect/actual: notifications, file saver, share, nfc (wire or delete)
└── util/           keep only live utils (TimeMath, AttendanceTimetable, SocialUtils…)
```

**Target size after extraction:** `AppState` 2,716 → ~400 lines (nav + theme + DI). `AmazeClient` 1,889 → ~1,000 (http core + DTOs). ~25 small repos, each 50-200 lines.

## 4. Sync coordinator (replaces the dead SyncEngine execution layer)

```kotlin
class SyncCoordinator(
    private val repos: Repositories,
    private val scope: CoroutineScope,
) {
    private val _progress = MutableStateFlow(SyncProgress())   // derived from repo states
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    suspend fun syncAll(enabled: Set<Domain>) {          // built from settings
        repos.filter { it.domain in enabled }.forEach { repo ->
            runCatching { repo.refresh() }
                .onFailure { _progress.update { it.failed(repo.domain, it) } }
        }
    }
}
```
Progress is *derived*, never hand-set — kills the `SyncEngine` race class entirely (eval/02 M5) and the fake-success modules (eval/01 §2).

## 5. Settings → typed reactive settings

Replace raw string keys + `getString/setString` (SettingsManager) with:

```kotlin
object SettingsKeys {                       // typed keys, single source
    val syncArrear      = BoolKey("sync_arrear")
    val notificationOffset = IntKey("notif_offset_minutes")
    val pinnedTabs      = StringListKey("pinned_tabs")   // Screen enum name list — version it
    val theme           = EnumKey("theme", AppTheme.SYSTEM)
}
class SettingsRepository(settings: Settings) {
    val syncArrear      = settings.flow(BoolKey, default = true)   // StateFlow-based
    val pinnedTabs      = settings.flow(StringListKey, default = defaults)
}
```
This fixes: `KEY_SYNC_ARREAR` never read back (M3), enum-name persistence fragility (L11), `course_note_*` unbounded keys, plaintext credentials (add an encrypted store — see eval/07).

## 6. Migration path (mechanical, safe, in ~6 steps)

1. **Freeze + delete (1-2 days):** apply `eval/05-dead-code.md` inventory. Restores iOS build (MarksSync), removes 19 dead files, ~5,000 LOC. Compile + run demo mode after.
2. **Extract a real `Repositories` container (3-5 days):** create `data/` with repos that *wrap existing AppState flows* (1:1 delegates, e.g. `AttendanceRepository { state = AppState.attendance }`). Wire `LocalRepositories` CompositionLocal in App.kt. **No behavior change yet** — screens keep collecting AppState flows; repos are additive scaffolding.
3. **Migrate screens to hooks one domain at a time (5-10 days):** for each screen, replace `AppState.x.collectAsState()` with `useX()`. Each migration deletes one AppState flow → measurable progress. Start with the small domains (QBank, Faculty, Moodle, Payments).
4. **Fold sync into `SyncCoordinator` (3-5 days):** `loadAllData` becomes a list of `repo.refresh()` calls; delete the dead `SyncEngine` execution API; progress becomes derived.
5. **Split AmazeClient (2-4 days):** group endpoints by domain API; keep the `object` as a thin facade until all screens migrate, then delete.
6. **Fix logout (1 day):** `LocalRepositories` container exposes `resetAll()`; logout calls it — kills the privacy bug C3 and the double-clears.

**Testing gate after each step:** `:androidApp:compileDebugKotlin` + demo-mode walkthrough of the 4 tab roots + FFCS + cabshare.

## 7. Component-level modularization (independent of the state refactor)

- Extract `KPICard`/`DataTableCard` once into `ui/components/` (5 duplicates exist — eval/03 §5).
- Extract `TimetableGrid` (the `FfcsTimetableGrid` cell logic) so the weekly dialog, AttendanceScreen, and Dashboard share one grid — with SlotMap as the single source (fixes H2 + grid drift).
- One `DayOfWeek`/slot constants file (`MON..SUN` is re-hardcoded 6+ times).
- One `gradePointMap` + one `grade→percent` table in `domain/` (3 copies exist).
- Merge `SelectHubField`/`SelectField`; merge the two cab hub lists; merge the two changelog lists.
- Kill the `Color.White`/exact-hex dispatch in `AmazeButton` (Components.kt:75) with a `contentFor(accent)` token lookup.

## 8. Rules of thumb going forward (put these in AGENTS.md)

1. **No new singleton `object` state.** State lives in a repo/VM created with a scope; inject via `CompositionLocal`.
2. **Screens read via `useXxx()` hooks only.** No `AppState.` or `AmazeClient.` imports in `ui/`.
3. **Flows exposed as `StateFlow` via `asStateFlow()`** — already the convention; enforce.
4. **Every public symbol needs ≥1 real caller** — dead code fails the build review.
5. **No fake success.** A failure must surface as an error state or a `Result`; UI greys, never lies.
6. **No hardcoded dates/semesters/credits** — go through `SettingsRepository` or a `CampusConfig` model fetched from the API.
7. **One source of truth per concept** — SlotMap, grade tables, hub lists, changelog, version.
8. **Endpoints return `Result<T>`** (or throw typed errors) — kill the `success=true` default pattern on ~35 models.
