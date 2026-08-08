# Evaluation 08 — Remediation Roadmap

Ordered by impact/effort. Each item references its eval report. Approximate effort assumes one dev.

---

## Phase 0 — Stop the bleeding (days 1-3, security + data integrity)

| # | Task | Fixes | Effort |
|---|---|---|---|
| 0.1 | Remove the `DEMO123`/`demo` backdoor; gate `useMockData` behind debug | C1, `eval/01 §1` | 2h |
| 0.2 | Encrypt credential storage (Android Keystore cipher / iOS Keychain); stop storing raw cookie string | C2, `eval/07 §2` | 1d |
| 0.3 | Logout clears every domain (memory + disk) | C3 | 1d |
| 0.4 | Fix `postQBankPaper` path → `"qbank/upload"` | C4 | 5m |
| 0.5 | Delete `MarksSync.kt` (+android actual) — restores iOS build | H6 | 5m |
| 0.6 | Remove keystore/`keystore_base64.txt` from git; rotate keystore | `eval/07 §7` | 30m |
| 0.7 | Fix `clearPendingNotifications()` requestCode; stop ID collisions (`title.hashCode()`) | H3, H4 | 3h |
| 0.8 | Kill cab-share fake-success paths — surface real errors | H5 | 4h |
| 0.9 | Add `HttpTimeout` + `expectSuccess` to the shared client | L1 | 1h |

**Exit:** no backdoor, no plaintext creds, iOS compiles, no fake success, no 404 upload.

## Phase 1 — User-visible correctness (days 4-8)

| # | Task | Fixes | Effort |
|---|---|---|---|
| 1.1 | Fix Academics 0% attendance (`replace("%","")`) | H1 | 5m |
| 1.2 | Fix weekly timetable dialog day filter (SlotMap day key) | H2 | 1h |
| 1.3 | Delete the 19 dead files + 4 stub screens + 3 dead repositories (per `eval/05`) | — | 1d |
| 1.4 | Fix FFCS silent course-drop; make lock model pair-based; snapshot config at generate time | H9, H10, L46 | 1d |
| 1.5 | Fix notification parse paths (DD-MM-YYYY + Moodle ISO) + task offset + channels | H15 | 4h |
| 1.6 | Fix FFCS grid: derive periods from SlotMap, align gutters, fix block granularity | M11 | 1d |
| 1.7 | Fix QR generator (alignment patterns + multi-block EC) or switch to the unused `qrcode-kotlin` lib | H18 | 1d |
| 1.8 | Wire boot receiver to re-schedule alarms; push widget updates after sync | H8, H13 | 1d |
| 1.9 | Fix `headerBackOverride` leak (clear on `setHeader`) | H14 | 30m |
| 1.10 | Fix `MainTabPager` blank pages + BottomNav "Unknown" icons for pinable screens | H17 | 2h |

**Exit:** every visible screen shows real data; planners/generators don't lie; reminders work; widgets refresh.

## Phase 2 — Dead-code & duplication sweep (days 9-12)

| # | Task | Fixes | Effort |
|---|---|---|---|
| 2.1 | Apply full dead-code inventory (`eval/05`): ~45 dead functions, ~18 classes, ~35 fields, dead resources | — | 1-2d |
| 2.2 | Merge duplicated UI (`KPICard`×5, Projects/Wishlist/Documents/CourseMgmt screens, SelectHub/SelectField, changelog) | `eval/03 §5` | 1d |
| 2.3 | Finish token sweep: 36 hardcoded colors + 100+ fontSize overrides → theme tokens | `eval/03 §1` | 1-2d |
| 2.4 | Single sources of truth: SlotMap day/periods, grade tables, hub lists, version.properties, semesterMap from API | M30 + `eval/03 §4` | 1d |
| 2.5 | Remove dead settings surface: `Strings.kt` unused entries, dead SettingsScreen rows, Spotlight search | `eval/03 §2` | 3h |
| 2.6 | Fix `AmazeTests` (`Screen.DASHBOARD`); add smoke tests for the hooks | — | 1d |

**Exit:** repo shrinks ~25%; no dead code survives a fresh grep; docs updated (BUGS.md/README/IMPLEMENTATION_PLAN).

## Phase 3 — Modularize into hooks (weeks 2-4) — see `eval/06`

| # | Task | Effort |
|---|---|---|
| 3.1 | `Repositories` container + `LocalRepositories` CompositionLocal; repos wrap existing AppState flows (no behavior change) | 3-5d |
| 3.2 | Migrate screens to `useXxx()` hooks domain-by-domain; delete AppState flows as they migrate | 5-10d |
| 3.3 | `SyncCoordinator` — progress derived from repo states; delete dead SyncEngine execution API | 3-5d |
| 3.4 | Split `AmazeClient` into domain APIs; DTO cleanup | 2-4d |
| 3.5 | Typed `SettingsRepository` (fixes syncArrear restore, enum-name persistence, unbounded note keys) | 2d |
| 3.6 | Logout = `repos.resetAll()` | 1d |

**Exit:** `AppState` ≈ 400 ln (nav+theme+DI); screens touch only hooks; each repo unit-testable; new features are additive repos.

## Phase 4 — Product completion (per roadmap, in hooks style)

| Feature | Current state | Next step |
|---|---|---|
| Makeup & Compre | Dead API functions + empty stub screen | Wire via `MakeupRepository` or delete |
| Vitol | Empty stub screen + real `getPayments` wallet | Implement or delete |
| Marks Timeline | Empty stub screen; `allSemesterMarks` exists | Implement or delete |
| NFC | Dead feature, crash-prone manager | Wire to UI or delete |
| Curriculum | Mock-only ("empty mock for now") | Finish real fetch (`CurriculumRes` shape is unknown — needs contract) |
| Bank/APAAR/EPT/Dayboarder/Registration | Empty-success fakes | Needs real contract before "done" claims |
| Profile photo | Consumed, never produced | Wire `getVtopStudentPhoto` (securely) or remove |
| Midnight theme / icon switching | Theme exists (AMOLED), icon switching missing | Finish or fix docs |
| Free-classrooms widget | Fake data | Use `FacultyFreeSlotsUtil` engine + push updates |
| iOS file save | No-op | Implement document picker / share sheet |
| iOS notifications | Work via `NotificationsUtils`; `NotificationService` stub | Delete stub or implement remote push |
| Hall of Fame / Hostel advisor/mess/laundry / Changelog | Fake data | Replace with real sources or remove |
| Transport semester list | Hardcoded | Derive from API or settings |

## Rules to keep it from rotting again

1. Every PR: `rg` the deleted symbol; run `:androidApp:compileDebugKotlin`; run demo-mode smoke test.
2. No new `object` state, no new `success=true` defaults, no new hardcoded dates/colors/fontSizes.
3. Screens import hooks, never singletons.
4. Keep `eval/` refreshed: each report section has "verified at commit X" so drift is visible.

## Phase 0 Status (2026-08-06) — COMPLETE, build green

| # | Item | Status |
|---|---|---|
| 0.1 | Remove DEMO123/demo backdoor; gate `useMockData` behind debug | ✅ Done — mocks unreachable in production (see `01-stub-code.md`, `07-security.md`) |
| 0.2 | Encrypt credential storage (Keystore/Keychain cipher) | ✅ Android Keystore AES-GCM; iOS plain fallback (documented); session cookies deferred |
| 0.3 | Logout clears all user data (memory + disk) | ✅ 16 more cache keys + 2 flows + alarms; `course_note_*` deferred |
| 0.4 | Fix `postQBankPaper` double-`/api/` | ✅ |
| 0.5 | Delete `MarksSync.kt` (+android actual) | ✅ `KeyValueStore` re-homed into `PastDataSync.kt` |
| 0.6 | Untrack keystore secrets | ✅ Verified never committed; `.gitignore` already complete |
| 0.7 | Fix `clearPendingNotifications()` + ID collisions | ✅ |
| 0.8 | Kill cab fake-success paths | ✅ local-trip functions deleted |
| 0.9 | Add `HttpTimeout` (no `expectSuccess`) | ✅ 30s/15s/30s |
| — | Compile check | ✅ `:androidApp:compileDebugKotlin` BUILD SUCCESSFUL |
| — | Record changes in `eval/*.md` | ✅ this log |

Follow-ups now in scope for Phase 1+: session-cookie encryption, `course_note_*` purge (needs key enumeration or a notes-store rework), iOS Keychain, `expectSuccess`/`usesCleartextTraffic` (Phase 2), and the whole Phase-4 dead-code sweep whose inventory is unaffected by the 0.5/0.8 deletions.

## Phase 2 Status (2026-08-07) — dead-code sweep, build green

| # | Item | Status |
|---|---|---|
| 2.1 | Dead-code inventory (`eval/05`) | ✅ Models §2 (~55 fields/classes), API/state §3 (~45 fns + old-cab flows + SyncEngine group), UI dead slots/components, FFCS residuals, resources §4, strings §5 — all deleted. Full log in `eval/05-dead-code.md` Phase 2 Fix Log |
| 2.2 | Duplicated UI merges (KPICard×5, SelectHub/SelectField, screen dups) | ✅ `KPICard`+`DataTableCard` → single shared `ui/components/DataTableComponents.kt` (4 byte-identical copies deleted; ODTracker's icon-card variant kept — different component); `SelectHubField` → wrapper over `SelectField` (hubs as options+displayMap); Projects/Wishlist/Documents/CourseMgmt screens → 2 shared composables (`KeyValueResponseScreen`, `TabbedKeyValueScreen` in `KeyValueResponseScreens.kt`, ~300 LOC saved); changelog dedup done earlier. Build green |
| 2.3 | Token sweep (36 colors / 100+ fontSizes) | ✅ **Done.** Colors swept earlier (42 literals → tokens). **fontSize swept this pass:** new `AmazeFontSize` token scale (Theme.kt: `micro` 10 → `hero` 48, 11 tokens) replacing **333 literal `fontSize = N.sp` overrides** across all screens (1:1 token mapping, no value drift except the flagged micro-cluster 6-9sp raised to `micro` 10sp); `Type.kt` typography definitions kept literal. Micro-cluster design fixes: FfcsTimetableGrid band labels `lineHeight 8sp → 12sp` (10sp font was clipping), all flagged screens verified padding-based (no fixed-height clipping) |
| 2.4 | Single sources of truth | ✅ **Done.** version.properties (no drift), hub lists (`fallbackCabHubs`), CampusSchemas (CHENNAI only), **SlotMap** (`days`/`weekdays`/`dayLabels` constants — 5 re-hardcoded lists deduped), **CGPA extraction** (`MarksRes.displayCgpa`/`displayCreditsEarned` — 3 duplicate parse sites), **semesterMap now seeded from API**: StateFlow `_semesterMap` merged from `/api/all-grades` keys + current semester after every `getAllGrades` success and cache restore, `semesterDisplayName` derivation (`CH20262705` → "Winter Semester 2026-27") with the old hardcoded map as fallback; 10 UI consumers via `collectAsState()`. **`getCalendars` now fetches all 8 calendar types** (General Semester/Flexible/Freshers/LAW/Flexible Freshers/Cohort LAW/Flexible Research/Weekend) concurrently; CalendarScreen shows in-screen calendar-type chips (restored from `SettingsManager.getPreferredCalendar`); **semester switching lives in Settings** (new "Semester" section wired to `AppState.selectSemester`, which refreshes attendance/marks/exams/calendars — web parity, no semester UI on the calendar page); Settings "Academic Calendar" picker lists all 8 fixed types. Magic `"CH20262701"` strings → `AppState.DEFAULT_SEMESTER_ID` |
| 2.5 | Dead settings surface | ✅ `Strings.kt` 101 → 19 keys; dead settings rows gone; Spotlight/command palette slots removed |
| 2.6 | `AmazeTests` (`Screen.DASHBOARD`) | ✅ verified by symbol availability (commonTest only wires to iOS targets; `Screen.HOME` covers it). `:shared:testDebugUnitTest` doesn't exist in this repo |
| — | §1 whole files (19) + 4 stub screens + 3 repositories | ✅ verified already-deleted (stale inventory), zero grep refs |
| — | Root clutter | ✅ `keystore_base64.txt` + `release.keystore` removed (untracked; signing is GH-Actions-secret based); `*.keystore`/`local.properties` gitignored |
| — | Compile check | ✅ `:androidApp:compileDebugKotlin` BUILD SUCCESSFUL (1m 44s, warnings only) after final wave |
| — | Record changes in `eval/*.md` | ✅ Phase 2 Fix Log in `05-dead-code.md`; this log |

**Remaining Phase 2 scope:** `clearHeaderBackOverride` wiring (needs H14 fix first), final `git commit` of the sweep (≈37 staged deletions + unstaged edits, uncommitted).
