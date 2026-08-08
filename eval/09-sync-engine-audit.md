# eval/09 — Sync Engine Audit & Fix Log

Date: 2026-08-08
Scope: `state/SyncEngine.kt`, `state/AppState.kt` (all sync paths), `ui/components/SyncProgressPopup.kt`, `ui/components/SyncSettingsDialog.kt`, `App.kt` wiring, onboarding sync path.

## Findings (pre-fix)

### 1. Percentages don't refresh during a sync (by design)
- `loadAllData()` calls `SyncEngine.markAllLoading()` (all 27 modules → LOADING) before the sweep, then **nothing updates any module to SUCCESS/ERROR while modules complete**. Each `syncModule()` only returns a `SyncModuleResult` into the results list.
- Module states only change at the END of the sweep via `updateModuleStatesFromCache()` (which marks SUCCESS only for modules whose data exists in memory) + `resetLoadingToIdle()`.
- Result: the ring/pill sits at 0% for the entire multi-minute sweep, then jumps. On a re-sync the app starts with SUCCESS states from cache-load, `markAllLoading` flips them all back → the ring visibly **drops from ~75% to 0%** at every sync tap.
- Error states are NEVER written to SyncEngine during sweeps. Failed modules end as IDLE. Consequences:
  - Modal `ModulesTabContent` can never show a red row or `ModuleState.error` text.
  - `syncProgress.errorCount` stays 0 while modules actually fail → popup auto-dismisses "clean" and shows "All modules updated", while the global error banner (`AppState._error` via `updateSyncSummary`) eventually shows failures. Popup and error banner contradict each other.

### 2. Progress computed over the wrong module set
- `recalculateProgress()` totals all 27 `SyncModule.entries`, but the sweep only actually runs a subset: profile×7 gated on `syncProfile`, EVENTS/CLUBS gated on clubToken, etc. With profile sync off, a fully-successful sweep can still end at IDLE for ~7+ modules → ring never completes, `displayText` claims partial.
- `SyncProgress.percentage` returns `100f` when `totalModules == 0` — fake 100% ring for a sweep that ran nothing.

### 3. Cancel is mostly fake
- `SyncEngine.activeJobs` is declared but **never populated** (no registration anywhere). `cancelAll()` cancels nothing; it just resets all states.
- `AppState.cancelSync()` cancels only `currentSyncJob`, which is assigned **only in `loadAllData`**. `loadSemesterData`, `refreshCalendar`, `refreshCalendarsList`, `refreshPayments`, `refreshHostel`, targeted refreshes, onboarding run on `scope` with no registered job → the modal Cancel button cannot stop them.
- The cancelled `loadAllData` coroutine still executes its `finally` → fires "Sync completed" notification and `scheduleReminders()` even on cancel.
- Once cancelled, `resetAllStates()` wipes module states, but in-flight sweep pieces that already fetched still write data/cache afterwards (late writers win).

### 4. Semester-switch mid-sweep = silent drop
- Every `refresh*`/`loadAllData`/`loadSemesterData` starts with `if (_isLoading.value) return` — a silent no-op.
- When a full sweep is running and the user switches semester (Settings → `selectSemester`), the switch's data load is dropped, and the running sweep syncs the OLD semester. The user believes data changed; it didn't.
- The `_isLoading` guard also makes every "Sync"/refresh button tap during any active sync silently do nothing (no feedback).

### 5. Modal UX problems
- `SyncProgressPopup` auto-shows on ANY `isLoading` state flip (`isSyncing = isAppStateSyncing || isAppStateLoading || isEngineSyncing`) — background refreshes (calendar, semester load) pop the full screen-scrim dialog even after the user dismissed it. `userDismissed = remember(isSyncing)` resets on every isSyncing transition → no durable dismissal.
- Full-screen scrim (zIndex 120, dims everything) blocks navigation; dismissing requires scrim tap or ✕ with no "stop showing this" affordance.
- Auto-dismiss exists only when `errorCount == 0`; because errors never register in SyncEngine, it auto-dismisses falsely-clean more often than not.
- Pill + dialog can stack (minimized pill's settings button opens the full dialog while pill intent...).

### 6. Misc
- `updateModuleStatesFromCache()` marks `SUCCESS, lastSynced = now` for modules merely loaded from disk (not synced) — fake freshness timestamps in Settings "Data Sync & Cache" list.
- Onboarding: `startOnboardingSync()` writes a parallel `_onboardingSyncSteps` flow, but `OnboardingScreen` Welcome/Completion cards read `SyncEngine.moduleStates` → onboarding shows all-pending, unfed (double source of truth).
- `saveOffline()` may interleave with in-flight sweep cache writes — acceptable, left as-is.

## Fixes applied

### A. SyncEngine — live per-module progress
- Added name → `SyncModule` resolution (`syncModuleByName`) with a curated mapping table (incl. `All Semesters Attendance` → `ALL_SEMESTER_ATTENDANCE`, `All Semesters` → `ALL_SEMESTER_ATTENDANCE`, `All Semesters Exam Schedule` → `EXAM_SCHEDULE`, `Registered Events` → `EVENTS`, sub-results of the profile group → `STUDENT_PROFILE`).
- Added `beginSweep(modules: Set<SyncModule>)` — resets all 27 to IDLE, sets each module in the run set to LOADING; `recalculateProgress` totals the run set, so a clean sweep completes 100% for **every** profile.
- `syncModule` now drives `SyncEngine` per module (LOADING → SUCCESS(lastSynced=now)/ERROR(msg)); failed modules keep visible error; `updateSyncSummary` is no longer the only error surface.
- `markAllLoading`/`resetLoadingToIdle` removed/replaced (`resetLoadingToIdle` no longer used).
- `SyncProgress.percentage` → 0f when `totalModules == 0` (no fake 100%).

### B. Cancel
- `activeJobs` now real: every sweep/refresh registers `Job` via `runSweep(block)`; `cancelAll()` cancels those jobs and clears states.
- `AppState.cancelSync()` cancels the registered current sweep job and offers `_syncMessage "Sync cancelled"`.
- `loadAllData` finally: on cancellation (guarded by `catch (CancellationException)` rethrow / flag), skips "Sync complete" notification + `scheduleReminders()`.

### C. Semester-switch chaining
- `selectSemester()` when a sweep is in progress (isLoading) → schedule a follow-up `loadSemesterData(newSem)` once the active sweep finishes instead of silently dropping (via a pending-flag re-checked in the sweep's finally).
- Guard feedback: sweeps no longer return silently on `_isLoading` when triggered by explicit user actions... (feedback via `_syncMessage`).

### D. Modal UX
- Popup auto-show only for `showSyncDialog` (user-initiated: header sync button, Settings Sync All, `setShowSyncDialog(true, …)`); background `isLoading` flips no longer auto-open it.
- Dismissal now durable: `userDismissed` is not reset by state flips (reset only via explicit new `setShowSyncDialog(true)`).
- Overview tab: explicit "Close" button when finished (incl. error states); banner uses engine data only.

### E. Onboarding
- `startOnboardingSync()` now walks the same `syncModule()` helper (writes SyncEngine states) and `OnboardingScreen` reads `SyncEngine.moduleStates` — single source of truth, live progress.

### F. SyncSettingsDialog / profile gating (broken settings)
- Root cause: profiles were cosmetic — `activeProfile`/`enabledModules` were never read by any sync path (`syncModule` didn't consult them; sweeps ran the hardcoded `syncPattern`).
- Fixed: `SyncEngine.isModuleEnabled(module)` = active profile `enabledModules` contains the module AND the legacy flag gates (`syncProfile`, `syncExam`, `syncAdditional`, `syncArrear`, `syncAllSemesterMarks`) for the modules they own. `syncModule()` short-circuits gated-off modules with a success result; `loadAllData` filters `sweepModules` through `isModuleEnabled`; all targeted refreshes (calendar, payments, hostel, transport, LMS, exam schedule, library, circulars, profile group, events/clubs, etc.) wrap `launchSweep(setOf(aliasOf(…)))` so disabled modules never mark LOADING.
- Note: settings UI itself was structurally fine; the fix is entirely in gating + live module states. Custom profiles now actually limit what syncs.

### G. Sweep-driven refresh plumbing + onboarding
- All targeted `refresh*` functions moved onto `launchSweep` (single-flight guard `_isLoading || SyncEngine.isAnyModuleLoading()`), replacing the old `scope.launch` + `_isLoading=true`/`finally=false` pattern; the job is registered (`SyncEngine.registerJob(Job)`, jobs stored in a `mutableSetOf<Job>`) so `cancelSync()` covers these paths too.
- Per-module LOADING/SUCCESS/ERROR driven through `syncModule` in nearly all paths (events/clubs marked explicitly in `syncEventsAndClubs`).
- `startOnboardingSync()` rewired through `launchSweep` + the same `syncModule` helpers (7 parallel `syncOnboarding*` fetch bodies consolidated); `_onboardingSyncSteps` keeps feeding the step list while `SyncEngine.moduleStates` gets live per-module updates.
- Removed `updateModuleStatesFromCache()`: cached (disk-restored) data no longer fakes `SUCCESS/lastSynced=now`; modules stay IDLE until a real sync writes them.

## Files touched
list:
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/SyncEngine.kt`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/SyncProgressPopup.kt`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/SyncSettingsDialog.kt`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/onboarding/OnboardingScreen.kt`

Build: `:androidApp:compileDebugKotlin` EXIT=0.

## Planned: Leveled Sync Automation (2026-08-08, agreed scope)

Stages / schedules:
- Onboarding forces the `full_sync` built-in profile (via `SyncEngine.withStageProfile`).
- After onboarding, a toned-down **light reload** runs on a user-configured schedule (Daily at HH:MM, or every N days at HH:MM) using the new `daily_reload` built-in profile: Attendance, Timetable, Marks, Exam Schedule, Academic Calendar, Calendars List, Moodle, Circulars, LMS.
- A **full reload** runs weekly (chosen weekday at HH:MM) using `full_sync` (28 modules, incl. the new Moodle entry).
- Built-in profiles stay editable; `resetProfileToBuiltin(id)` restores canonical sets from `DEFAULT_SYNC_PROFILES` (persisted profiles are merged with missing built-ins on load).
- Enforcement is invisible: `withStageProfile` overrides `activeProfile` resolution only; `activeProfileId`/chips/persisted settings untouched; override cleared in `finally`.
- Platform firing: Android exact-alarm (`SyncAlarmReceiver`, self-rescheduling, existing `CHANNEL_SYNC` + exact-alarm permission) + reboot re-hook; iOS schedules a reminder notification and syncs on app foreground (`AppState.checkDueSync()` on App.kt launch + `MainActivity.onResume`).
- If a sync is already running when a scheduled run is due: skip + roll next window (no stacking).
- Dead legacy toggles (`syncExam`, `syncProfile`, `syncAdditional`, `syncArrear`) removed — profile gating is the single source of truth.

### Implementation steps
1. `SyncEngine.kt`: `MOODLE` entry + alias "Moodle Assignments"; `daily_reload` built-in; profile merge on load; `resetProfileToBuiltin(id)`; `withStageProfile(profileId, block)`.
2. `SettingsManager.kt`: `CACHE_MOODLE`; automation keys (`KEY_AUTO_SYNC_ENABLED`, light rule, full rule, `KEY_NEXT_LIGHT`, `KEY_NEXT_FULL`, `KEY_LAST_SYNCED_AT`); remove legacy 4 keys.
3. `AppState.kt`: remove legacy flows/setters/init reads + `if (syncProfile.value)` gate; moodle-into-sweep helper (`NO_MOODLE_CREDS` → resetModule); `"Moodle Assignments"` in `loadAllData` sweep; onboarding wrapped `withStageProfile("full_sync")`; `runScheduledSync(light|full)` + `checkDueSync()`.
4. New `shared/.../state/SyncScheduler.kt` (rule computation + persistence) + new `utils/SyncAlarm.kt` expect/actual (Android exact alarm; iOS reminder notification).
5. Android: `SyncAlarmReceiver`, manifest entry, `BootReceiver` hook, `MainActivity.onResume` → `checkDueSync()`.
6. Settings UI: remove 4 legacy toggles; add "Sync Automation" card (master switch, cadence + time pickers, profile chooser, run-now, next/last run labels); `SyncSettingsDialog` reset-to-defaults per built-in.
7. Verify: `:androidApp:compileDebugKotlin` EXIT=0 + iOS source set compile; receiver smoke test via adb broadcast.

### Leveled sync automation — implementation log (2026-08-08)

Steps 1–7 executed (Android compile EXIT=0; iOS set compiled-authored — needs macOS host to verify):

1. **SyncEngine.kt** — `MOODLE("Moodle Assignments")` added (+aliases); `daily_reload` built-in (`ATT, TIMETABLE, MARKS, EXAM, CAL, CALENDARS_LIST, MOODLE, CIRCULARS, LMS`); Full profile renamed "Full Sync (28 Modules)"; `withStageProfile` (invisible, `finally`-restored); `loadProfiles()` v0→v1 one-time merge of missing built-ins (later loads preserve user edits; customs appended); `resetProfileToBuiltin(id)`.
2. **SettingsManager.kt** — `KEY_SYNC_PROFILES_VERSION=1`, `KEY_AUTO_SYNC_ENABLED`, light keys (`KEY_LIGHT_RECURRENCE/INTERVAL_DAYS/HOUR/MINUTE/PROFILE_ID`), full keys (`KEY_FULL_DAY_OF_WEEK/HOUR/MINUTE/PROFILE_ID`), `KEY_NEXT_LIGHT_SYNC`, `KEY_NEXT_FULL_SYNC`, `KEY_LAST_SYNCED_AT`, `CACHE_MOODLE` (raw strings in AppState replaced).
3. **AppState.kt** — legacy flows/setters/init reads deleted; profile-group `if (syncProfile.value)` wrapper removed (profile set now plain sweep; result `profResults.firstOrNull { !it.success } ?: SyncModuleResult("Student Profile", true)`); `"Moodle Assignments"` sweep async (`creds == null` → `resetModule(MOODLE)` + success — never errors); onboarding wrapped in `withStageProfile("full_sync")`; `launchSweep(modules, stageProfile = …)` applies override inside the job so gating is correct for the whole sweep; `loadAllData(scheduledFor = …)` persists next-run in its `finally`; `runLightReload()` — dedicated 9-module light sweep (session refresh + attendance(marks), timetable, grade history, exam schedule, academic calendar, calendars list, LMS, circulars, moodle) with `markSynced()` + `advanceAndArm(LIGHT)`; `runScheduledSync(kind, force)` (force = settings "Run Now" bypasses master switch); `checkDueSync()` — full-before-light, skips when not logged in or a sweep is active, advances the other kind when both due.
4. **SyncScheduler.kt** (new) — rule getters/setters persist + `rescheduleAlarms()`; `nextOccurrence` (weekly / daily / every-N-days w/ anchor + rule time-of-day); `advanceAndArm(kind)` no-op when automation disabled; `markSynced()`; `SyncAlarm.kt` expect + Android actual (AlarmManager one-shot + PendingIntent FLAG_IMMUTABLE, exact-alarm permission fallback to inexact) + iOS actual (UNUserNotificationCenter reminder id 9010; cancel removes pending request).
5. **Android wiring** — `SyncAlarmReceiver` (extra `sync_kind` → `AppState.runScheduledSync(kind)`), manifest receiver entry, `BootReceiver` → `SyncScheduler.rescheduleAlarms()`, `MainActivity.onCreate` + `onResume` → `AppState.checkDueSync()`, common `App.kt` LaunchedEffect (iOS catch-up).
6. **Settings UI** — 4 legacy toggles removed (`syncExam/syncProfile/syncAdditional/syncArrear`); "Sync Automation" card: master switch, expandable Light/Full editors (profile picker dialog w/ radiogroup, Daily/Every-N-days recurrence, weekday chips Mon–Sun, hour/minute steppers, next-run labels), "Run Light Now"/"Run Full Now" force buttons; `SyncSettingsDialog` gains Restore (reset-to-defaults) icon on built-in profiles.
7. **Build** — `:androidApp:compileDebugKotlin` EXIT=0. iOS actuals use only public `platform.UserNotifications` APIs; still needs macOS verification. adb smoke test pending device.

Post-steps integrity notes:
- `runScheduledSync(force=true)` from settings runs without arming alarms (`advanceAndArm` no-ops when disabled).
- While logged out scheduled runs advance + skip silently.
- Busy guard: both run paths and `checkDueSync` bail when `_isLoading || isAnyModuleLoading` — no stacking.

### ANR / BinderProxy-storm fix (2026-08-08, device reports)

User hit two ANRs on-device (input-dispatch timeout at 18:04, then binder stress while toggling notifications). Logcat evidence: `BinderProxy map growth 2567→8583`, "Unexpectedly many live BinderProxies: 5000", "Skipped 751 frames" — classic main-thread binder saturation.

Root cause: `NotificationsUtils.android.kt` `clearPendingNotifications()` iterated the full `scheduleableNotificationIds` space (~5,999 ids: 1000..1999, 2000..3999, 4000..9998, 9999), doing `PendingIntent.getBroadcast` + `AlarmManager.cancel` per id (~18–24k binder transactions) on the MAIN thread. Every `rescheduleNotifications()` (Task/Class Reminders toggle) and every post-sync `scheduleReminders()` triggered it — a 10+ s main-thread stall on loaded devices (Load 8.04). This also explains the earlier cold-start ANR: first reschedule after boot.

Fix (compiled EXIT=0):
1. Reminders persist actually-scheduled ids in SharedPreferences `"amazecc_scheduled_reminder_ids"` (StringSet `"ids"`); `clearPendingNotifications()` cancels only tracked ids (`takeScheduledIds()`), then clears the set — O(actual) instead of O(6k).
2. `scheduleAll` moved `Dispatchers.Main` → `Dispatchers.Default` — all PendingIntent/alarm binder work off the main thread.
3. `App.kt` cold start: `loadFromCache()` (~24 JSON decodes) wrapped in `withContext(Dispatchers.Default)`.
4. Reviewed & cleared: DashboardWidgets 60 s timers, SplashScreen, SyncEngine/diff loops, SyncProgressPopup — none are hot paths.
5. SettingsScreen was externally reverted mid-session (automation card + legacy-toggle removal lost) — re-applied against the current search/categories file version; duplicate helper functions removed.

Build: `:androidApp:compileDebugKotlin` EXIT=0. Verification pending: device reinstall + toggle Task/Class reminders + a completed sync, watching for BinderProxy growth / frame skips; adb receiver smoke test for the scheduled alarm.