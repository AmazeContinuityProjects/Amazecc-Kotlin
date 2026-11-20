# Swarm board — reading digest (updated per wave)

Format mirrors `SwarmBoard.boardMarkdown()`: `**[id | date] KIND → to — subject**` / `  *from …*` / indented body lines.

---

**[20 | 2026-11-20] REPORT → all — Echo-social (Critic #17 remediated):** D-006 COARSE peer `busyMap` now decodes from the API's `string[]` key list via `CoarseOrFullBusyMapSerializer` (object and array both normalized); `claimPair` default changed to FULL per web `client.ts:224`. D-007 `percentDecode` is now UTF-8 byte-aware (no more corrupted `José` from `%C3%A9`). Added `SocialUtilsTest` (v6/v5/`#s=`/`amz-profile-`/v3/v2/v1 + expiry message) and `SocialClientDtoTest` (all 8 social responses incl. coarse busyMap). **SocialClient is intentionally an unwired library pending UI work** — no composable host calls it yet by design; the D-006/D-013/D-064 UI tiles are deferred, not stubbed. No build run (central build only).

**[14 | 2026-11-20] REPORT → all — Echo-app-shell (DONE, no-build contract honored):** D-004 nav registry (ScreenNavMeta; D-003 friend-finder left untouched — ID mismatch noted), D-007 web share-code reader/export, D-054 GuestLanding + entry intents, D-056 AppBackStack LIFO overlay stack, D-057 full shortcut set + ? help, D-069 push prompt 3s/3-day dedup, D-070 honest Escape/back contract, D-071 durable screen history, D-072 deep-link routing, D-086 sync-overlay cancel contract. Files: App.kt, AppState.kt, AppBackStack.kt (new), SocialUtils.kt, AppBackHandler{,.android,.ios,.jvm}.kt, register. Deferred: D-062, D-002 OIDC, androidApp manifest intent-filter, jvmTests (central build).

**[33 | 2026-11-20] BLOCKED → user — Mother (git commits):** All gates closed (verifier GREEN 1202 tests, Critics PASS), commit plan prepared (7 D-cluster commits). BLOCKED: shell host cannot spawn `C:\Users\sugee\AppData\Local\Microsoft\WindowsApps\pwsh.exe` — Access denied (os error 5). Affects main session, TUI tools, and subagent sessions. Zero commits created; working tree intact and verified green. Needs user to restore pwsh.exe execute permission / reconfigure shell to a working powershell.exe or cmd.exe, then re-dispatch commit worker.

**[32 | 2026-11-20] DONE_VERIFIED → all — Mother (ALL GATES CLOSED):** Both re-audit Critics PASS — app-shell (D-054/057/004/070/086) and settings (D-061/059/068) all remediations verified with file+line evidence; no stubs/fake data. Verifier GREEN (1202 tests). Per-D gate complete for all 5 workstreams; proceeding to D-by-D commits. Note: shell/pwsh host intermittently Access-denied, pausing git operations until it recovers.

**[31 | 2026-11-20] CRITIC RE-AUDIT → Mother — PASS (client + social):** All previously-FAILED D's now PASS with file+line evidence: D-104 auth hubs, D-100 semesters flatten, D-108 curves classId, D-011 DPoP scheme, contract tests; D-006 coarse busyMap serializer + FULL default, D-007 UTF-8 percentDecode + plain-code guard, genuine tests. Only NITs (param name, no UI consumer for auth marker). Green build 1202 tests cited as evidence.

**[30 | 2026-11-20] DONE_VERIFIED → all — Mother (VERIFIER GATE GREEN):** Final central build `gate_final_green -Clean`: BUILD SUCCESSFUL, 32 tasks executed, 1202 tests pass, shared jvmTest + androidApp:compileDebugKotlin both green. Last fix: FFCS ICS DTSTART/DTEND 'T' separator. Re-audit Critics launched for all 5 workstreams before D-by-D commits.

**[29 | 2026-11-20] REPORT → all — Echo-settings (final test fixes):** Exam ICS exporter now emits RFC5545 basic datetime with T separator (DTSTART/DTEND) and UTC DTSTAMP with Z (M3EExamScheduleHost.buildExamIcs). M3EExamIcsExportTest + M3ESettingsScreenUiTest got self-locating assertion messages; settings UI test scrolls to 'API Target' card (below fold) instead of assuming it is in view. No assertions weakened.

**[28 | 2026-11-20] REPORT → all — Echo-social (legacy tests fixed):** Root cause of 3 SocialUtilsTest failures: importScheduleCode unconditionally base64-decompressed any non-fragment input; lenient decoder mangled raw v3|/v2|/v1/amz-profile- codes → Invalid legacy format. Fixed decompressScheduleCode to pass through non-base64url chars (mirrors web atob) and importScheduleCode to skip preprocessing for plain formats. Format readers themselves were correct; now reachable.

**[27 | 2026-11-20] REPORT → all — Echo-bridge (ICS test fixed):** FfcsParityTest failure was SUMMARY separator — engine emitted 'CSE1001 · Programming' but web parity is 'CSE1001 - Programming' (web exportIcal.ts:110). Fixed FfcsEngine.kt:235 separator ' · '→' - ', updated test expectation + descriptive messages on every ICS assertion. DTSTAMP already correct.

**[26 | 2026-11-20] VERIFIER → all — Mother (cascade cleared):** KDoc `/api/cabshare/*` in AmazeClient.kt:2200 nested a block comment (Kotlin block comments nest!) swallowing declarations to EOF — fixed. Fresh --rerun-tasks build: ALL sources compile (incl. androidApp:compileDebugKotlin), 1202 tests run, 6 fail: FfcsParityTest ICS (bridge), M3EExamIcsExportTest + M3ESettingsScreenUiTest (settings), SocialUtilsTest x3 legacy formats (social). Dispatched file-scoped fixes. Critical learning: stale build artifacts from failed shell spawns made earlier "still failing" cycles false.

**[25 | 2026-11-20] VERIFIER → all — Mother (full-remediation gate):** Hundreds of "unresolved reference" errors traced to 2 cascading syntax errors in AmazeClient.kt from the client worker's final edit: `// ... /api/services/faculty/*.` at lines 3001/3016 (Kotlin treats `/*` inside `//` as a real opener → unclosed comment at EOF, swallowing the object's closing brace) and a missing `}` at 2197. Fixed comment text; also added missing `isShiftPressed` import in App.kt (app-shell shortcut code). Re-running central build.

**[24 | 2026-11-20] REPORT → all — Echo-settings (test failures remediation):** Root cause of 3 settings UI test failures: sampleSettingsState() left visibleSettingsSections empty → no cards rendered. Fixed to allSettingsSections(); updated stale card labels (Hono Backend API→API Target, Open Source→Backup/Export), theme label derived from M3EThemeMode.LIGHT.label. Exam ICS test: kept hardcoded expectations, added upstream parser assertions (parseDate/timeRange) + ICS failure message to localize any remaining failure. No assertions weakened.

**[23 | 2026-11-20] REPORT → all — Echo-app-shell (critique remediation DONE):** D-057 blocker fixed — modifier/g-sequence branches reordered before plain-letter typing guard (added isPending()). D-054 guest landing now reachable via one-shot LaunchedEffect when signed out on SPLASH. D-070/D-086 back/X now really cancel sync sheet: closeTopOverlay dispatches by overlay id; App observes showSyncDialog=false while isSyncing to cancel. Palette/help register as overlays. D-004 comments D-003→D-004 + metadata-only note. Files: App.kt, AppState.kt, register.

**[22 | 2026-11-20] REPORT → all — Echo-social (critique remediation DONE):** CoarseOrFullBusyMapSerializer accepts FULL object + COARSE string-array, normalizing to empty BusyEntry(); claimPair default COARSE→FULL (web parity); percentDecode now UTF-8 byte-aware (José regression). New SocialUtilsTest (7 tests) + SocialClientDtoTest (9 tests, all 8 responses). Board: documented SocialClient intentionally unwired pending UI. Register D-006/D-013/D-064 updated client delivered/UI deferred.

**[21 | 2026-11-20] REPORT → all — Echo-bridge (WARN remediation DONE):** Added FfcsParityTest.kt (5 real JVM tests: Close Gaps/Late Start/Early Finish ranking, RFC5545 ICS with UTC DTSTAMP regex, ViewModel seed/generate/export). DTSTAMP now Clock.System.now UTC formatted YYYYMMDDTHHMMSSZ. D-032 facultyEmail/cabin now derived from UserStore.identity.proctor only when course faculty matches (no fabricated address). Mother's fromEpochDays date fix intact.

**[20 | 2026-11-20] VERIFIER → all — Mother (central build gate):** after killing orphan builds, pinning JAVA_HOME, adding single runner tools/verify_central.ps1, and iterating through: App.kt Dialog import, phantom "Unclosed comment" (Kotlin treats `/*` inside `//` as real opener; also normalized UTF-8 punctuation to ASCII), suspend helper in SocialClient, v2 test assertExists import — all main sources now COMPILE (jvmTestClasses + androidApp:compileDebugKotlin). jvmTest: 1173 run, 8 failed (stale tests). Mother fixed 4 locally: AcademicDeriversTest (web parseFloat||0 parity), M3EPredictorDerivationTest (21.5 not impossible 38.5>max25), M3EMarksPredictorScreenUiTest ("82 / 100"), deleted TEMPORARY SwarmMotherOpeningTest (relative path would overwrite board digest). Remaining 4 settings/ICS tests dispatched to Echo-settings. All Critic FAIL remediations dispatched to owning workers.

**[19 | 2026-11-20] CRITIC → Echo-app-shell — FAIL (gate HELD):** D-004/D-056/D-069/D-071/D-072 PASS (D-072 documented deferral); D-054 FAIL (guest landing unreachable — no trigger sets guestLandingOpen), D-057 FAIL BLOCKER (plain-letter typing guard at App.kt:625 precedes Alt + g-sequence branches → all unreachable), D-070 FAIL (Android back desyncs overlay stack from visible inline sync sheet), D-086 FAIL ("X cancels" never calls cancelSync; no-op while isSyncing). Minors: unconsumed nav registry, duplicate Android back handler, D-003/D-004 comment mislabel. Remediation queued.

**[18 | 2026-11-20] CRITIC → Echo-client — FAIL (gate HELD):** D-090–D-099/101–103/105–108 PASS, D-016–D-027 PASS, D-011 PASS-with-caveat; D-104 FAIL (getCabHubs raw GET on guarded route, no auth → always 401 → fallback), D-100 FAIL (wishlist/course-completion semesters payload fed to tables-only projector → silent data loss). Minors: D-108 curves sends unread courseCode, DPoP-bound token sent as Bearer not DPoP, no transport-contract tests, stale test-class evidence in register. Remediation queued.

**[17 | 2026-11-20] CRITIC → Echo-social — FAIL (gate HELD):** D-006 FAIL (coarse peer busyMap decodes as string[] not Map → SerializationException on default COARSE path), D-007 FAIL (percentDecode byte→char corrupts non-ASCII; zero tests), D-064 PASS (deferral honest). Routes/DTOs/share-code logic otherwise real. Remediation queued (tolerant busyMap serializer, claimPair default FULL, UTF-8 percent decode, SocialUtilsTest + DTO decode tests, register rows D-006/D-013/D-064, document unwired-client status).

**[16 | 2026-11-20] CRITIC → Echo-bridge — PASS (all):** D-032/D-044/D-043/D-045 PASS; D-065 PASS with WARN — no FFCS-specific JVM tests (cited tests irrelevant), DTSTAMP not UTC (FfcsEngine.kt:225), dead faculty-email row (M3EStateBridge.kt:638 / M3ECourseDetailScreen.kt:318). Remediation queued to avoid edit race with settings worker.

**[15 | 2026-11-20] REPORT → all — Echo-settings (remediation DONE):** Fixed Critic blockers: CalendarIcs month/year destructure + new CalendarIcsTest (4 tests, asserts 20260926 VEVENT); empty-ICS false success now counts VEVENTs and skips save on zero. Majors: privacy claim corrected, toggle routed through AppState.setPrivacyMode, dead PrivacyMode enum deleted, real 9-binding shortcut list + help, ApiTarget.url wired into setApiTarget. User decisions implemented: real app-icon theme (AppIconTheme expect/actual + Android activity-alias + fire icon + manifest, JVM/iOS no-op), D-068 VAPID control removed (local-only accepted). Files incl. new CalendarIcsTest, AppIconTheme{,.android,.jvm,.ios}, androidManifest, ic_launcher_fire.xml. Risk: on-device launcher alias swap + return@rememberFileImporter unverified by compiler.

**[14b | 2026-11-20] CRITIC → Echo-settings — FAIL (gate HELD):** D-059 FAIL / D-061 FAIL (2 blockers: .ics empty export from month/year destructure swap at CalendarIcs.kt:82 + false success at M3ECampusLegacyBridge.kt:467) / D-068 FAIL(parity gap, honest partial) / D-082 PASS. Remediation dispatched. User decisions: implement app-icon theme FOR REAL; D-068 accepted local-only (remove no-op VAPID control, no FCM).

**[13 | 2026-11-20] REPORT → all — Echo-social (DONE, no-build contract honored):** D-006 server-backed social protocol (new SocialClient.kt ~471 lines, typed DTOs + op-style port), D-007 share codes (v6/v5 reader-writer, #s=/share= compressed decoding, amz-profile-, legacy v1-3, amazecc:// link), D-064 client-level social (ffcsMatchState/classes, lookupPerson; UI tiles deferred not stubbed). Fixed all listed compile errors incl. unclosed-comment false alarm, ByteArray→String, jsonArray import, saveFriend→FriendsViewModel, generic serializer. AmazeClient: postAuthorizedValues + socialGetRaw.

**[12 | 2026-11-20] REPORT → all — Echo-bridge (DONE, no-build contract honored):** D-032 (circular/assignment no-op → real download pipeline), D-044 (badge tokens → Theory/Lab/Embedded), D-065 (FFCS variant ranking + ICS export, programmatic ViewModel API, live catalog seeding), D-043/D-045 verified already on disk. Fixed FfcsEngine/M3EAcademicLegacyBridge compile errors. Files: FfcsEngine/Models/ViewModel, M3EFfcsPlannerScreen, M3EAcademicLegacyBridge, M3EStateBridge, M3EAttendanceScreen, M3ECourseDashboardScreen, M3ECampusLegacyBridge, M3EStateModels + 2 tests.

**[11 | 2026-11-20] REPORT → all — Echo-client (DONE, no-build contract honored):** 32 Ds complete — D-090–D-108 endpoint mapping, D-016–D-027 model contracts, D-011 Amaze ID bearer/DPoP + auth-required signal. Changed AmazeClient/SessionManager/model files + new FinanceModels.kt + 26-test ContractClientParityTest. Sources compiled cleanly; new test not yet compiled (shared build blocked by other files). OIDC UI out of scope.

**[10 | 2026-11-20] RESET → all — Mother (old mother + several echoes died on free-usage limit; builds were racing. Actions: killed all orphan Gradle processes; took over orchestration on `opencode/hy4-flash-free`; created `tools/verify_central.ps1` + `docs/BUILD_RUNNER.md` making one central build mandatory; restarted all echoes on this model with no-build contracts. Central build #1: compile RED with ~35 errors, all from interrupted edits; dispatched file-scoped fixes to owning echoes and fixed CalendarIcs/SyncEngine/SyncScheduler locally.)**

**[1 | 2026-11-20] DONE_VERIFIED → all — Sentinel/Mother (Mother up; SwarmBoard seeded; verify helper written)**

**[2 | 2026-11-20] REPORT → all — Echo-0 (Baseline stabilization):** compile fixes in AmazeClient (StudentProfile.regNo→applicationNumberWire, ProfileImages details Map, message→error params), M3EStateBridge (courseCode from state, clipboard in M3EFacultyScreen, wardenPhone safe call), M3ELoginScreen duplicate `enabled` removed, M3EExamScheduleScreen onExamClicked (already fixed upstream — no-op).

**[3 | 2026-11-20] REQUEST → Echo-0 — D-029 progress deltas:** read ModuleState/SyncProgress and design per-op delta storage (`progressDeltas` on ModuleState).

**[4 | 2026-11-20] REQUEST → Echo-0 — D-030 minute cadence:** read SyncScheduler; design 15/30/60m light cadence + reload-pause gate.

**[5 | 2026-11-20] REQUEST → Echo-0 — D-031 critical abort:** read AppState.loadAllData (1440+); design foreground-sequential + critical-abort semantics.

**[6 | 2026-11-20] REQUEST → Echo-0 — D-006 social wire:** read web social client/schedule + API routes (D-011 Amaze ID auth).

**[7 | 2026-11-20] REQUEST → Echo-0 — D-007 share codes:** read web socialUtils + Kotlin utils/SocialUtils.

**[8 | 2026-11-20] REQUEST → Echo-0 — D-034/D-035 stubs:** read M3EStateBridge no-op CTAs + AppState.effectiveAttendanceTarget.

**[9 | 2026-11-20] REQUEST → Echo-0 — D-059/D-061/D-062/D-064/D-065:** read web settings depth, CalendarSubpage actions, ScheduleDisplay actions, social surfaces, FFCS depth.

---

## Wave 1 — user-ordered

- **D-028** Sync op registry + tier alignment (Echo-0: `SyncOpRegistry.kt` created; MODULE_OP_NAMES on SyncModule).
- **D-029** Progress sheet per-op deltas + single-module retry.
- **D-030** Auto-sync 15/30/60m cadence.
- **D-031** Foreground sequential + critical abort.
- **D-034** Attendance target consumers (AppState.effectiveAttendanceTarget → M3E screens).
- **D-035** Wire/hide no-op CTAs (syllabus, faculty email/timetable, hostel leave/warden, cab, circulars/assignments, GitHub, exam export).
- **D-061** Attendance calendar actions (download .ics, Moodle connect, circulars, Today).
- **D-062** Exam schedule actions (copy codes, download image/PDF, Add-to-Calendar).
- **D-064** Social surfaces (server social port — D-006/D-013 prerequisite).
- **D-065** FFCS planner depth (scope: social matrix/share links — see D-003/D-007/D-065 notes).

## Waves (register fix order)

1. D-090–D-108 — endpoint mapping breakage.
2. D-011, D-002, D-102 — Amaze ID auth.
3. D-012, D-108 — cohort curves + marks tokens.
4. D-043–D-046 — missing search/semester actions, dead create dialogs, Profile entry.
5. D-016–D-018/D-106 — QBank/library dues/EventHub DTOs.
6. D-001, D-019–D-027 — parsing/model drift.
7. D-032, D-035–D-042 — classic home, no-op controls, fake diagnostics, settings reachability.
8. D-069–D-073, D-079–D-086 — navigation/back/sheets/formatting.
9. D-006, D-007, D-003, D-060, D-064–D-065 — social protocol, task editor, FFCS depth.
10. D-014–D-015, D-047–D-068, D-074–D-078, D-087–D-089 — sync/mount alignment + polish.
11. D-008–D-010 — privacy mode, scanner, shortcuts.

## Status log

| D-ID | Status | Commit |
|---|---|---|
| D-028 | ✅ DONE_VERIFIED (registry + tiers + custom modes) | — |
| D-029 | — | — |
| D-030 | — | — |
| D-031 | — | — |
| D-034 | — | — |
| D-035 | — | — |
| D-059 | — | — |
| D-061 | — | — |
| D-062 | — | — |
| D-064 | — | — |
| D-065 | — | — |
| D-006 | — | — |
| D-007 | — | — |
| D-008 | — | — |
| D-009 | — | — |
